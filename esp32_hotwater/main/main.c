#include <stdio.h>
#include <stdbool.h>
#include <string.h>

#include "esp_check.h"
#include "esp_crt_bundle.h"
#include "esp_event.h"
#include "esp_http_client.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "esp_system.h"
#include "esp_timer.h"
#include "esp_wifi.h"
#include "freertos/FreeRTOS.h"
#include "freertos/event_groups.h"
#include "freertos/task.h"
#include "nvs.h"
#include "nvs_flash.h"
#include "driver/gpio.h"

#if __has_include("hotwater_secrets.h")
#include "hotwater_secrets.h"
#else
#include "hotwater_config.h"
#endif

#define WIFI_CONNECTED_BIT BIT0
#define WIFI_FAIL_BIT      BIT1

#define NVS_NAMESPACE "hotwater"
#define NVS_KEY_ORDER_NO "order_no"

#define OPEN_ENDPOINT "/order/ali/tcpDevice/downRate/rateOrder"
#define OPEN_RESULT_ENDPOINT "/order/tcpDevice/query/downRateResult"
#define CLOSE_ENDPOINT "/order/tcpDevice/closeOrder"
#define CLOSE_RESULT_ENDPOINT "/order/tcpDevice/closeOrder/result/query"
#define STATUS_ENDPOINT "/order/tcpDevice/query/rateOrder/using"

#define API_HOST "https://v3-api.china-qzxy.cn"

static const char *TAG = "hotwater";
static EventGroupHandle_t s_wifi_event_group;
static int s_retry_num = 0;
static bool s_request_in_flight = false;

typedef struct {
    int64_t pressed_at_us;
    bool press_active;
} button_state_t;

static button_state_t s_button_state = {0};

typedef struct {
    char response[2048];
    size_t length;
} http_response_buffer_t;

static void status_led_set(bool on)
{
    gpio_set_level(HOTWATER_STATUS_LED_GPIO, on ? 1 : 0);
}

static void status_led_blink(int count, int on_ms, int off_ms)
{
    for (int i = 0; i < count; ++i) {
        status_led_set(true);
        vTaskDelay(pdMS_TO_TICKS(on_ms));
        status_led_set(false);
        vTaskDelay(pdMS_TO_TICKS(off_ms));
    }
}

static esp_err_t save_order_no(const char *order_no)
{
    nvs_handle_t handle;
    ESP_RETURN_ON_ERROR(nvs_open(NVS_NAMESPACE, NVS_READWRITE, &handle), TAG, "nvs_open failed");
    esp_err_t err = nvs_set_str(handle, NVS_KEY_ORDER_NO, order_no);
    if (err == ESP_OK) {
        err = nvs_commit(handle);
    }
    nvs_close(handle);
    return err;
}

static esp_err_t load_order_no(char *out, size_t out_size)
{
    nvs_handle_t handle;
    ESP_RETURN_ON_ERROR(nvs_open(NVS_NAMESPACE, NVS_READONLY, &handle), TAG, "nvs_open failed");
    size_t required = out_size;
    esp_err_t err = nvs_get_str(handle, NVS_KEY_ORDER_NO, out, &required);
    nvs_close(handle);
    return err;
}

static void clear_order_no(void)
{
    nvs_handle_t handle;
    if (nvs_open(NVS_NAMESPACE, NVS_READWRITE, &handle) == ESP_OK) {
        nvs_erase_key(handle, NVS_KEY_ORDER_NO);
        nvs_commit(handle);
        nvs_close(handle);
    }
}

static void wifi_event_handler(void *arg, esp_event_base_t event_base, int32_t event_id, void *event_data)
{
    if (event_base == WIFI_EVENT && event_id == WIFI_EVENT_STA_START) {
        esp_wifi_connect();
    } else if (event_base == WIFI_EVENT && event_id == WIFI_EVENT_STA_DISCONNECTED) {
        if (s_retry_num < 10) {
            esp_wifi_connect();
            s_retry_num++;
            ESP_LOGW(TAG, "retrying Wi-Fi connection");
        } else {
            xEventGroupSetBits(s_wifi_event_group, WIFI_FAIL_BIT);
        }
    } else if (event_base == IP_EVENT && event_id == IP_EVENT_STA_GOT_IP) {
        s_retry_num = 0;
        xEventGroupSetBits(s_wifi_event_group, WIFI_CONNECTED_BIT);
    }
}

static void wifi_init_sta(void)
{
    s_wifi_event_group = xEventGroupCreate();

    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());
    esp_netif_create_default_wifi_sta();

    wifi_init_config_t cfg = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&cfg));

    ESP_ERROR_CHECK(esp_event_handler_instance_register(WIFI_EVENT,
                                                        ESP_EVENT_ANY_ID,
                                                        &wifi_event_handler,
                                                        NULL,
                                                        NULL));
    ESP_ERROR_CHECK(esp_event_handler_instance_register(IP_EVENT,
                                                        IP_EVENT_STA_GOT_IP,
                                                        &wifi_event_handler,
                                                        NULL,
                                                        NULL));

    wifi_config_t wifi_config = {0};
    strlcpy((char *)wifi_config.sta.ssid, HOTWATER_WIFI_SSID, sizeof(wifi_config.sta.ssid));
    strlcpy((char *)wifi_config.sta.password, HOTWATER_WIFI_PASSWORD, sizeof(wifi_config.sta.password));
    wifi_config.sta.threshold.authmode = WIFI_AUTH_WPA2_PSK;

    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));
    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &wifi_config));
    ESP_ERROR_CHECK(esp_wifi_start());

    EventBits_t bits = xEventGroupWaitBits(
        s_wifi_event_group,
        WIFI_CONNECTED_BIT | WIFI_FAIL_BIT,
        pdFALSE,
        pdFALSE,
        pdMS_TO_TICKS(30000));

    if (bits & WIFI_CONNECTED_BIT) {
        ESP_LOGI(TAG, "Wi-Fi connected");
        status_led_blink(2, 120, 120);
    } else {
        ESP_LOGE(TAG, "Wi-Fi connect failed");
        status_led_blink(6, 100, 100);
    }
}

static esp_err_t http_event_handler(esp_http_client_event_t *evt)
{
    http_response_buffer_t *buffer = (http_response_buffer_t *)evt->user_data;
    if (evt->event_id == HTTP_EVENT_ON_DATA && evt->data_len > 0) {
        size_t writable = sizeof(buffer->response) - buffer->length - 1;
        size_t copy_len = evt->data_len < writable ? evt->data_len : writable;
        if (copy_len > 0) {
            memcpy(buffer->response + buffer->length, evt->data, copy_len);
            buffer->length += copy_len;
            buffer->response[buffer->length] = '\0';
        }
    }
    return ESP_OK;
}

static esp_err_t build_form_body(char *out, size_t out_size, const char *order_no, bool include_order_auth_code)
{
    int written = snprintf(
        out,
        out_size,
        "accountId=%d&loginCode=%s&phoneSystem=%s&projectId=%d&snCode=%s&telephone=%s&userId=%d&version=%s%s%s",
        HOTWATER_ACCOUNT_ID,
        HOTWATER_LOGIN_CODE,
        HOTWATER_PHONE_SYSTEM,
        HOTWATER_PROJECT_ID,
        HOTWATER_SN_CODE,
        HOTWATER_TELEPHONE,
        HOTWATER_USER_ID,
        HOTWATER_VERSION,
        order_no ? "&orderNo=" : "",
        order_no ? order_no : "");

    if (include_order_auth_code) {
        written += snprintf(out + written, out_size - written, "&orderAuthCode=");
    }

    return (written > 0 && (size_t)written < out_size) ? ESP_OK : ESP_ERR_NO_MEM;
}

static esp_err_t post_endpoint(const char *endpoint, const char *body, http_response_buffer_t *buffer)
{
    char url[192];
    snprintf(url, sizeof(url), "%s%s", API_HOST, endpoint);

    esp_http_client_config_t config = {
        .url = url,
        .method = HTTP_METHOD_POST,
        .event_handler = http_event_handler,
        .user_data = buffer,
        .crt_bundle_attach = esp_crt_bundle_attach,
        .timeout_ms = 15000,
    };

    esp_http_client_handle_t client = esp_http_client_init(&config);
    if (client == NULL) {
        return ESP_FAIL;
    }

    esp_http_client_set_header(client, "Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
    esp_http_client_set_header(client, "Accept", "application/json, text/plain, */*");
    esp_http_client_set_post_field(client, body, (int)strlen(body));

    esp_err_t err = esp_http_client_perform(client);
    if (err == ESP_OK) {
        ESP_LOGI(TAG, "%s -> HTTP %d", endpoint, esp_http_client_get_status_code(client));
        ESP_LOGI(TAG, "response: %s", buffer->response);
    } else {
        ESP_LOGE(TAG, "%s request failed: %s", endpoint, esp_err_to_name(err));
    }

    esp_http_client_cleanup(client);
    return err;
}

static bool response_has_success(const char *response)
{
    return strstr(response, "\"success\":true") != NULL;
}

static bool response_has_order_no(const char *response, char *out_order_no, size_t out_size)
{
    const char *key = "\"orderNo\":\"";
    const char *start = strstr(response, key);
    if (!start) {
        return false;
    }

    start += strlen(key);
    const char *end = strchr(start, '"');
    if (!end) {
        return false;
    }

    size_t len = (size_t)(end - start);
    if (len + 1 > out_size) {
        return false;
    }

    memcpy(out_order_no, start, len);
    out_order_no[len] = '\0';
    return true;
}

static esp_err_t hotwater_open(void)
{
    char body[512];
    http_response_buffer_t buffer = {0};
    ESP_RETURN_ON_ERROR(build_form_body(body, sizeof(body), NULL, true), TAG, "build body failed");
    ESP_RETURN_ON_ERROR(post_endpoint(OPEN_ENDPOINT, body, &buffer), TAG, "open request failed");

    char order_no[64] = {0};
    if (response_has_order_no(buffer.response, order_no, sizeof(order_no))) {
        ESP_LOGI(TAG, "saved orderNo from open response: %s", order_no);
        ESP_RETURN_ON_ERROR(save_order_no(order_no), TAG, "save orderNo failed");
    }

    status_led_blink(response_has_success(buffer.response) ? 2 : 8, 100, 120);
    return ESP_OK;
}

static esp_err_t hotwater_poll_open_result(void)
{
    char order_no[64] = {0};
    if (load_order_no(order_no, sizeof(order_no)) != ESP_OK) {
        ESP_LOGW(TAG, "no cached orderNo for open result");
        return ESP_ERR_NOT_FOUND;
    }

    char body[512];
    http_response_buffer_t buffer = {0};
    ESP_RETURN_ON_ERROR(build_form_body(body, sizeof(body), order_no, false), TAG, "build body failed");
    ESP_RETURN_ON_ERROR(post_endpoint(OPEN_RESULT_ENDPOINT, body, &buffer), TAG, "open result request failed");

    char refreshed_order_no[64] = {0};
    if (response_has_order_no(buffer.response, refreshed_order_no, sizeof(refreshed_order_no))) {
        ESP_LOGI(TAG, "updated orderNo from open result: %s", refreshed_order_no);
        save_order_no(refreshed_order_no);
    }

    return ESP_OK;
}

static esp_err_t hotwater_close(void)
{
    char order_no[64] = {0};
    if (load_order_no(order_no, sizeof(order_no)) != ESP_OK) {
        ESP_LOGW(TAG, "no cached orderNo; cannot close");
        status_led_blink(10, 60, 60);
        return ESP_ERR_NOT_FOUND;
    }

    char body[512];
    http_response_buffer_t buffer = {0};
    ESP_RETURN_ON_ERROR(build_form_body(body, sizeof(body), order_no, false), TAG, "build body failed");
    ESP_RETURN_ON_ERROR(post_endpoint(CLOSE_ENDPOINT, body, &buffer), TAG, "close request failed");
    status_led_blink(response_has_success(buffer.response) ? 3 : 10, 90, 90);
    return ESP_OK;
}

static esp_err_t hotwater_poll_close_result(void)
{
    char order_no[64] = {0};
    if (load_order_no(order_no, sizeof(order_no)) != ESP_OK) {
        return ESP_ERR_NOT_FOUND;
    }

    char body[512];
    http_response_buffer_t buffer = {0};
    ESP_RETURN_ON_ERROR(build_form_body(body, sizeof(body), order_no, false), TAG, "build body failed");
    ESP_RETURN_ON_ERROR(post_endpoint(CLOSE_RESULT_ENDPOINT, body, &buffer), TAG, "close result request failed");

    if (response_has_success(buffer.response)) {
        clear_order_no();
    }
    return ESP_OK;
}

static esp_err_t hotwater_status(void)
{
    char body[512];
    http_response_buffer_t buffer = {0};
    ESP_RETURN_ON_ERROR(build_form_body(body, sizeof(body), NULL, false), TAG, "build body failed");
    ESP_RETURN_ON_ERROR(post_endpoint(STATUS_ENDPOINT, body, &buffer), TAG, "status request failed");
    return ESP_OK;
}

static void button_gpio_init(void)
{
    gpio_config_t button_cfg = {
        .pin_bit_mask = 1ULL << HOTWATER_BUTTON_GPIO,
        .mode = GPIO_MODE_INPUT,
        .pull_up_en = GPIO_PULLUP_ENABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    ESP_ERROR_CHECK(gpio_config(&button_cfg));

    gpio_config_t led_cfg = {
        .pin_bit_mask = 1ULL << HOTWATER_STATUS_LED_GPIO,
        .mode = GPIO_MODE_OUTPUT,
        .pull_up_en = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    ESP_ERROR_CHECK(gpio_config(&led_cfg));
    status_led_set(false);
}

static void handle_button_action(bool long_press)
{
    if (s_request_in_flight) {
        ESP_LOGW(TAG, "request already running");
        return;
    }

    s_request_in_flight = true;
    status_led_set(true);

    if (long_press) {
        ESP_LOGI(TAG, "long press -> close hot water");
        if (hotwater_close() == ESP_OK) {
            vTaskDelay(pdMS_TO_TICKS(1800));
            hotwater_poll_close_result();
        }
    } else {
        ESP_LOGI(TAG, "short press -> open hot water");
        if (hotwater_open() == ESP_OK) {
            vTaskDelay(pdMS_TO_TICKS(1800));
            hotwater_poll_open_result();
        }
    }

    status_led_set(false);
    s_request_in_flight = false;
}

static void button_task(void *arg)
{
    int last_level = 1;

    while (1) {
        int current_level = gpio_get_level(HOTWATER_BUTTON_GPIO);
        int64_t now_us = esp_timer_get_time();

        if (last_level == 1 && current_level == 0) {
            s_button_state.press_active = true;
            s_button_state.pressed_at_us = now_us;
            vTaskDelay(pdMS_TO_TICKS(HOTWATER_DEBOUNCE_MS));
        } else if (last_level == 0 && current_level == 1 && s_button_state.press_active) {
            int64_t duration_ms = (now_us - s_button_state.pressed_at_us) / 1000;
            s_button_state.press_active = false;
            handle_button_action(duration_ms >= HOTWATER_LONG_PRESS_MS);
            vTaskDelay(pdMS_TO_TICKS(HOTWATER_DEBOUNCE_MS));
        }

        last_level = current_level;
        vTaskDelay(pdMS_TO_TICKS(20));
    }
}

void app_main(void)
{
    esp_err_t nvs_err = nvs_flash_init();
    if (nvs_err == ESP_ERR_NVS_NO_FREE_PAGES || nvs_err == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        nvs_err = nvs_flash_init();
    }
    ESP_ERROR_CHECK(nvs_err);
    button_gpio_init();
    wifi_init_sta();

    /* 上电后先打一遍状态，便于串口调试 */
    hotwater_status();

    xTaskCreate(button_task, "button_task", 4096, NULL, 5, NULL);
}

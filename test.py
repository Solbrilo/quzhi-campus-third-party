# test_connection.py
import requests

requests.packages.urllib3.disable_warnings()

url = "https://v3-api.china-qzxy.cn/user/info"
params = {
    "projectId": 3796,
    "userId": 19738568,
    "loginCode": "2d3c7929870345bbd3bcb97ad2eff2ee",
}

try:
    print("正在测试连接...")
    response = requests.get(url, params=params, verify=False, timeout=10)
    print(f"状态码: {response.status_code}")

    if response.status_code == 200:
        data = response.json()
        user_account = data.get("data", {}).get("userAccount", {})
        raw_money = user_account.get("accountRealMoney", 0)
        balance = raw_money / 1000

        print("连接成功")
        print(f"用户: {data.get('data', {}).get('telephone')}")
        print(f"余额: {balance:.2f} 元")
    else:
        print(f"请求失败: {response.status_code}")
        print(f"响应: {response.text}")

except Exception as e:
    print(f"异常: {e}")

# hotwater_fixed_device.py
import requests
import json
import time
import os
import warnings
from typing import Dict, Optional
from urllib3.exceptions import InsecureRequestWarning

# 禁用SSL警告
warnings.filterwarnings("ignore", category=InsecureRequestWarning)

# 清除所有代理设置
os.environ['HTTP_PROXY'] = ''
os.environ['HTTPS_PROXY'] = ''
os.environ['NO_PROXY'] = '*'

class YunnanUniversityHotWater:
    def __init__(self, login_code: str, user_id: int, account_id: int, phone: str, 
                 mac_address: str = "C47F0EDA85D8", project_id: int = 3796):
        """
        初始化热水控制器
        
        Args:
            login_code: 登录令牌 (loginCode)
            user_id: 用户ID
            account_id: 账户ID
            phone: 手机号
            mac_address: 设备MAC地址（从抓包数据获取）
            project_id: 项目ID（云南大学为3796）
        """
        self.base_url = "https://v3-api.china-qzxy.cn"
        
        # 创建会话，禁用SSL验证
        self.session = requests.Session()
        self.session.verify = False
        
        # 用户信息
        self.login_code = login_code
        self.user_id = user_id
        self.account_id = account_id
        self.phone = phone
        self.mac_address = mac_address
        self.project_id = project_id
        
        # 设备信息（稍后获取）
        self.device_sn = None
        self.device_info = None
        
        # 通用请求头
        self.headers = {
            "User-Agent": "Mozilla/5.0 (Linux; HarmonyOS)",
            "Accept": "application/json",
            "Content-Type": "application/json"
        }
        
        # 通用查询参数
        self.base_params = {
            "projectId": self.project_id,
            "userId": self.user_id,
            "loginCode": self.login_code,
            "accountId": self.account_id,
            "telephone": self.phone
        }
    
    def _get(self, endpoint: str, extra_params: Dict = None) -> Dict:
        """发送GET请求"""
        url = f"{self.base_url}{endpoint}"
        params = self.base_params.copy()
        
        if extra_params:
            params.update(extra_params)
        
        try:
            response = self.session.get(
                url, 
                params=params,
                headers=self.headers,
                timeout=10
            )
            print(f"GET {endpoint} -> {response.status_code}")
            return response.json()
        except Exception as e:
            print(f"请求失败: {e}")
            return {"success": False, "error": str(e)}
    
    def _post(self, endpoint: str, extra_params: Dict = None, data: Dict = None) -> Dict:
        """发送POST请求"""
        url = f"{self.base_url}{endpoint}"
        params = self.base_params.copy()
        
        if extra_params:
            params.update(extra_params)
        
        try:
            response = self.session.post(
                url,
                params=params,
                json=data or {},
                headers=self.headers,
                timeout=10
            )
            print(f"POST {endpoint} -> {response.status_code}")
            return response.json()
        except Exception as e:
            print(f"请求失败: {e}")
            return {"success": False, "error": str(e)}
    
    def get_device_info(self) -> Dict:
        """获取设备信息（关键步骤）"""
        print("正在获取设备信息...")
        
        params = {
            "macAddress": self.mac_address,
            "phoneSystem": "Harmony",
            "version": "0.0.1"
        }
        
        response = self._get("/device/info/mac", params)
        
        if response.get("success"):
            self.device_info = response.get("data", {})
            self.device_sn = self.device_info.get("deviceSn")
            
            print(f"✅ 设备信息获取成功")
            print(f"  设备SN: {self.device_sn}")
            print(f"  设备名称: {self.device_info.get('deviceName')}")
            print(f"  设备位置: {self.device_info.get('deviceLocation')}")
            
            return self.device_info
        else:
            print(f"❌ 设备信息获取失败: {response.get('errorMessage')}")
            return {}
    
    def get_user_info(self) -> Dict:
        """获取用户信息"""
        return self._get("/user/info")
    
    def get_balance(self) -> float:
        """获取账户余额（元）"""
        params = {
            "telPhone": self.phone,
            "phoneSystem": "Harmony",
            "version": "0.0.1"
        }
        response = self._get("/account/wallet/money", params)
        
        if response.get("success"):
            money = response.get("data", {}).get("accountRealMoney", 0)
            return money / 100
        return 0.0
    
    def is_using_hot_water(self) -> bool:
        """检查是否正在使用热水"""
        if not self.device_sn:
            print("⚠️ 请先获取设备信息")
            return False
        
        # 根据你的抓包数据，正确的查询方式可能是：
        # 第22-23行: /order/tcpDevice/query/rateOrder/using
        # 但需要设备参数
        
        response = self._get("/order/tcpDevice/query/rateOrder/using")
        print(f"热水使用状态查询: {response}")
        
        # 如果返回错误，尝试另一种方式
        if not response.get("success"):
            # 尝试使用设备SN查询
            print("尝试其他查询方式...")
            # 这里可能需要其他API端点
        
        # 根据响应判断
        return response.get("data") is not None
    
    def close_hot_water(self) -> Dict:
        """关闭热水"""
        if not self.device_sn:
            return {"success": False, "message": "请先获取设备信息"}
        
        print("正在关闭热水...")
        
        # 根据抓包数据第25行: /order/tcpDevice/closeOrder
        # 可能需要设备SN参数
        
        # 尝试直接调用（根据你的抓包，这个端点可能不需要额外参数）
        close_response = self._post("/order/tcpDevice/closeOrder")
        print(f"关闭指令响应: {close_response}")
        
        if not close_response.get("success"):
            # 尝试带设备SN
            data = {"deviceSn": self.device_sn}
            close_response = self._post("/order/tcpDevice/closeOrder", data=data)
            print(f"带设备SN的关闭响应: {close_response}")
        
        if not close_response.get("success"):
            return {"success": False, "message": "关闭指令失败", "response": close_response}
        
        # 查询关闭结果（第26行）
        for i in range(3):
            time.sleep(2)
            result_response = self._get("/order/tcpDevice/closeOrder/result/query")
            print(f"关闭结果查询 {i+1}: {result_response}")
            
            if result_response.get("success"):
                return {"success": True, "message": "热水已关闭", "response": result_response}
        
        return {"success": False, "message": "关闭结果查询超时"}
    
    def open_hot_water(self) -> Dict:
        """开启热水"""
        if not self.device_sn:
            return {"success": False, "message": "请先获取设备信息"}
        
        print("正在开启热水...")
        
        # 根据抓包数据第27行: /order/ali/tcpDevice/downRate/rateOrder
        # 错误提示需要 deviceSn
        
        # 尝试带设备SN的请求
        data = {"deviceSn": self.device_sn}
        open_response = self._post("/order/ali/tcpDevice/downRate/rateOrder", data=data)
        print(f"开启指令响应: {open_response}")
        
        if not open_response.get("success"):
            # 尝试其他可能的参数
            data = {
                "deviceSn": self.device_sn,
                "projectId": self.project_id,
                "accountId": self.account_id
            }
            open_response = self._post("/order/ali/tcpDevice/downRate/rateOrder", data=data)
            print(f"带完整参数的开启响应: {open_response}")
        
        if not open_response.get("success"):
            return {"success": False, "message": "开启指令失败", "response": open_response}
        
        # 查询开启结果（第24行）
        for i in range(3):
            time.sleep(3)
            result_response = self._get("/order/tcpDevice/query/downRateResult")
            print(f"开启结果查询 {i+1}: {result_response}")
            
            if result_response.get("data", {}).get("isSuccess"):
                # 查询消费详情（第28行）
                time.sleep(2)
                consume_response = self._get("/order/consumeOrder/result/query")
                
                consume_data = consume_response.get("data", {})
                money = consume_data.get("consumeMoney", 0) / 100
                balance = consume_data.get("accountRealMoney", 0) / 100
                
                return {
                    "success": True,
                    "message": "热水已开启",
                    "consume_money": money,
                    "balance": balance,
                    "details": {
                        "open_response": open_response,
                        "result_response": result_response,
                        "consume_response": consume_response
                    }
                }
        
        return {"success": False, "message": "开启结果查询超时"}
    
    def toggle_hot_water(self) -> Dict:
        """一键开关热水（主功能）"""
        print("=" * 50)
        print("云南大学热水系统一键控制")
        print("=" * 50)
        
        # 1. 显示用户信息
        user_info = self.get_user_info()
        if user_info.get("success"):
            data = user_info.get("data", {})
            print(f"👤 用户: {data.get('telephone')}")
            print(f"🆔 用户ID: {data.get('userId')}")
        
        # 2. 显示余额
        balance = self.get_balance()
        print(f"💰 账户余额: {balance:.2f}元")
        
        # 3. 获取设备信息（关键！）
        device_info = self.get_device_info()
        if not self.device_sn:
            return {"success": False, "message": "无法获取设备信息"}
        
        # 4. 检查状态（简化，直接尝试操作）
        print("\n⚡ 执行操作...")
        
        # 先尝试关闭（如果正在使用）
        print("尝试关闭热水（如果正在使用）...")
        close_result = self.close_hot_water()
        
        if close_result["success"]:
            print("✅ 热水已关闭")
            time.sleep(2)  # 等待一下
            
        # 然后开启热水
        print("尝试开启热水...")
        open_result = self.open_hot_water()
        
        if open_result["success"]:
            print(f"✅ 热水已成功开启")
            print(f"💸 本次消费: {open_result.get('consume_money', 0):.2f}元")
            print(f"💰 剩余余额: {open_result.get('balance', 0):.2f}元")
            return open_result
        else:
            print(f"❌ 开启失败: {open_result['message']}")
            return open_result


def main():
    """主函数"""
    # ========== 配置信息 ==========
    # 从你的抓包数据中获取
    CONFIG = {
        "login_code": "2d3c7929870345bbd3bcb97ad2eff2ee",
        "user_id": 19738568,
        "account_id": 2047,
        "phone": "15812044530",
        "mac_address": "C47F0EDA85D8",  # 从抓包第11行获取
        "project_id": 3796
    }
    
    # ========== 创建控制器 ==========
    controller = YunnanUniversityHotWater(
        login_code=CONFIG["login_code"],
        user_id=CONFIG["user_id"],
        account_id=CONFIG["account_id"],
        phone=CONFIG["phone"],
        mac_address=CONFIG["mac_address"],
        project_id=CONFIG["project_id"]
    )
    
    # ========== 执行一键开关 ==========
    result = controller.toggle_hot_water()
    
    # ========== 显示详细信息 ==========
    print("\n" + "=" * 50)
    print("操作完成！")
    print("=" * 50)


def debug_mode():
    """调试模式：测试所有API"""
    import requests
    
    # 配置
    CONFIG = {
        "login_code": "2d3c7929870345bbd3bcb97ad2eff2ee",
        "user_id": 19738568,
        "account_id": 2047,
        "phone": "15812044530",
        "project_id": 3796
    }
    
    base_params = {
        "projectId": CONFIG["project_id"],
        "userId": CONFIG["user_id"],
        "loginCode": CONFIG["login_code"],
        "accountId": CONFIG["account_id"],
        "telephone": CONFIG["phone"]
    }
    
    def test_api(endpoint, method="GET", extra_params=None, data=None):
        url = f"https://v3-api.china-qzxy.cn{endpoint}"
        params = {**base_params, **(extra_params or {})}
        
        print(f"\n测试 {method} {endpoint}")
        print(f"参数: {params}")
        
        if method.upper() == "GET":
            response = requests.get(url, params=params, verify=False, timeout=10)
        else:
            response = requests.post(url, params=params, json=data, verify=False, timeout=10)
        
        print(f"状态码: {response.status_code}")
        print(f"响应: {response.text[:200]}...")
        return response.json()
    
    print("=" * 60)
    print("API调试模式")
    print("=" * 60)
    
    # 1. 测试设备信息
    device_params = {
        "macAddress": "C47F0EDA85D8",
        "phoneSystem": "Harmony",
        "version": "0.0.1"
    }
    device_info = test_api("/device/info/mac", extra_params=device_params)
    
    # 提取设备SN
    device_sn = device_info.get("data", {}).get("deviceSn")
    print(f"\n设备SN: {device_sn}")
    
    # 2. 测试开启热水（带设备SN）
    if device_sn:
        open_data = {"deviceSn": device_sn}
        test_api("/order/ali/tcpDevice/downRate/rateOrder", method="POST", data=open_data)
    
    # 3. 测试关闭热水
    test_api("/order/tcpDevice/closeOrder", method="POST")
    
    # 4. 测试状态查询
    test_api("/order/tcpDevice/query/rateOrder/using")


if __name__ == "__main__":
    # 运行主程序
    main()
    
    # 或者运行调试模式（取消注释下面一行）
    # debug_mode()

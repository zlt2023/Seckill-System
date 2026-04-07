import requests
import time
import concurrent.futures
import sys
import os

BASE_URL = "http://localhost:8080/api"
NUM_USERS = 200 # 压测并发用户数
SECKILL_GOODS_ID = 1 # 需要压测的秒杀商品ID

# 存放已认证的用户 token 信息
user_tokens = []

def setup_users():
    print(f"[*] 开始准备 {NUM_USERS} 个测试用户用于压测...")
    session = requests.Session()
    session.headers.update({"Content-Type": "application/json"})
    
    for i in range(1, NUM_USERS + 1):
        username = f"stress_test_user_{i}"
        password = "StressUser123!"
        phone = f"288{i:08d}"
        
        # 尝试注册
        reg_payload = {"username": username, "password": password, "phone": phone, "nickname": f"压测号{i}"}
        reg_res = session.post(f"{BASE_URL}/auth/register", json=reg_payload)
        
        # 尝试登录获取Token
        login_payload = {"username": username, "password": password}
        login_res = session.post(f"{BASE_URL}/auth/login", json=login_payload).json()
        
        if login_res.get('code') == 200:
            token = login_res['data']['token']
            user_tokens.append(token)
            if i % 50 == 0:
                print(f"    已就绪 {i} 个用户...")
        else:
            print(f"[!] 用户 {username} 初始化失败: {login_res}")
            
    print(f"[*] 准备完毕，成功获取 {len(user_tokens)} 个有效Token。\n")

def worker(token):
    session = requests.Session()
    session.headers.update({"Authorization": f"Bearer {token}"})
    
    # 步骤1：获取动态路径
    path_res = session.get(f"{BASE_URL}/seckill/path/{SECKILL_GOODS_ID}")
    path_data = path_res.json()
    
    if path_data.get('code') != 200:
        return {"status": "path_fail", "msg": path_data.get('message', '获取路径失败')}
        
    path_token = path_data['data']['pathToken']
    
    # 步骤2：执行秒杀
    exec_res = session.post(f"{BASE_URL}/seckill/{path_token}/execute?seckillGoodsId={SECKILL_GOODS_ID}")
    exec_data = exec_res.json()
    
    if exec_data.get('code') == 200:
        return {"status": "success", "msg": exec_data.get('data', '排队中')}
    else:
        return {"status": "exec_fail", "msg": exec_data.get('message', '秒杀失败')}

def start_stress_test():
    if not user_tokens:
        print("[!] 无法压测：没有有效的用户 Token。")
        return
        
    print(f"[*] === 开始高并发压测 (商品ID: {SECKILL_GOODS_ID}) ===")
    print(f"[*] 发起 {len(user_tokens)} 线程大军同时抢购...")
    
    results = {"success": 0, "path_fail": 0, "exec_fail": 0}
    reasons = {}
    
    start_time = time.time()
    
    # 使用线程池模拟高并发
    with concurrent.futures.ThreadPoolExecutor(max_workers=NUM_USERS) as executor:
        # submit 所有任务
        future_to_worker = {executor.submit(worker, token): token for token in user_tokens}
        
        for future in concurrent.futures.as_completed(future_to_worker):
            try:
                res = future.result()
                results[res["status"]] += 1
                
                # 记录失败原因用于统计
                msg = res["msg"]
                if res["status"] != "success":
                    if msg in reasons:
                        reasons[msg] += 1
                    else:
                        reasons[msg] = 1
                        
            except Exception as exc:
                results["exec_fail"] += 1
                print(f"[!] 一个线程抛出异常: {exc}")
                
    end_time = time.time()
    duration = end_time - start_time
    tps = len(user_tokens) * 2 / duration # 获取路径+执行请求 共2次
    
    print("\n[*] === 压测报告 ===")
    print(f"耗时: {duration:.2f} 秒")
    print(f"预估吞吐量 (Requests/sec): {tps:.2f}")
    print(f"总参与线程: {len(user_tokens)}")
    print(f"秒杀排队成功: {results['success']} 单")
    print(f"被拦截/获取路径失败: {results['path_fail']} 次")
    print(f"秒杀彻底失败: {results['exec_fail']} 次")
    
    if reasons:
        print("\n[*] 失败拦截分布分析:")
        for reason, count in reasons.items():
            print(f"    - {reason} : {count} 次")

if __name__ == "__main__":
    if len(sys.argv) > 1:
        try:
            SECKILL_GOODS_ID = int(sys.argv[1])
        except ValueError:
            print("参数错误，请提供合法的活动ID。")
            sys.exit(1)
            
    print(f"----- Spring Boot 秒杀接口并发压测工具 -----")
    setup_users()
    start_stress_test()

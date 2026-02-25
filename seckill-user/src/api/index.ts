import request from '../utils/request'

// ==================== 用户接口 ====================
export const userApi = {
    login: (data: { phone: string; password: string }) =>
        request.post('/user/login', data),

    register: (data: { username: string; phone: string; password: string }) =>
        request.post('/user/register', data),

    logout: () =>
        request.post('/user/logout'),

    getUserInfo: () =>
        request.get('/user/info')
}

// ==================== 商品接口 ====================
export const goodsApi = {
    list: () =>
        request.get('/goods/list'),

    detail: (seckillGoodsId: number) =>
        request.get(`/goods/detail/${seckillGoodsId}`)
}

// ==================== 验证码接口 ====================
export const captchaApi = {
    getSeckillCaptcha: (seckillGoodsId: number) =>
        request.get(`/captcha/seckill/${seckillGoodsId}`)
}

// ==================== 秒杀接口 ====================
export const seckillApi = {
    /** 获取秒杀路径(验证码通过后) */
    getPath: (seckillGoodsId: number, captcha: number) =>
        request.get(`/seckill/path/${seckillGoodsId}`, { params: { captcha } }),

    /** 执行秒杀(需要动态path) */
    doSeckill: (path: string, seckillGoodsId: number) =>
        request.post(`/seckill/${path}/do/${seckillGoodsId}`),

    /** 查询秒杀结果 */
    getResult: (seckillGoodsId: number) =>
        request.get(`/seckill/result/${seckillGoodsId}`)
}

// ==================== 订单接口 ====================
export const orderApi = {
    list: (status?: number) =>
        request.get('/order/list', { params: status !== undefined ? { status } : {} }),

    detail: (orderId: number) =>
        request.get(`/order/detail/${orderId}`),

    pay: (orderId: number) =>
        request.post(`/order/pay/${orderId}`),

    cancel: (orderId: number) =>
        request.post(`/order/cancel/${orderId}`),

    stats: () =>
        request.get('/order/stats')
}

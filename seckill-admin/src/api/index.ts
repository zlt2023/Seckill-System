import request from '../utils/request'

// ==================== 认证接口 ====================
export const authApi = {
    login: (data: { phone: string; password: string }) =>
        request.post('/user/login', data),

    logout: () =>
        request.post('/user/logout'),

    getUserInfo: () =>
        request.get('/user/info')
}

// ==================== 管理后台接口 ====================
export const adminApi = {
    /** 获取仪表盘数据 */
    dashboard: () =>
        request.get('/admin/dashboard'),

    /** 重置秒杀库存 */
    resetStock: (seckillGoodsId: number, stock: number) =>
        request.post(`/admin/reset-stock/${seckillGoodsId}?stock=${stock}`),

    /** 获取全量订单列表 */
    orders: (status?: number) =>
        request.get('/admin/orders', { params: status !== undefined ? { status } : {} }),

    /** 添加商品 */
    addGoods: (data: any) =>
        request.post('/admin/goods', data),

    /** 更新商品 */
    updateGoods: (id: number, data: any) =>
        request.put(`/admin/goods/${id}`, data),

    /** 删除商品 */
    deleteGoods: (id: number) =>
        request.delete(`/admin/goods/${id}`)
}

// ==================== 商品接口 ====================
export const goodsApi = {
    list: () =>
        request.get('/goods/list'),

    detail: (seckillGoodsId: number) =>
        request.get(`/goods/detail/${seckillGoodsId}`)
}

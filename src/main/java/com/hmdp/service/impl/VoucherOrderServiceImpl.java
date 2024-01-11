package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始！");
        }
        //3.判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束！");
        }
        //4.判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足！");
        }
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) { //锁开始的地方
            //获取事务有关的代理对象（事务）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }//锁结束的地方
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //实现一人一单 ---2024年1月8日
        //(1) 查询订单
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
            //(2) 判断订单是否已经存在
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if (count > 0) {
                return Result.fail("用户已经购买过一次，吴强");
            }

            //5.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    //.gt("stock", 0)
                    .update();
            if (!success) {
                return Result.fail("库存不足！");
            }

            //6.创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            //6.1 订单id
            voucherOrder.setId(orderId);
            //6.2 用户id
            //Long userId = UserHolder.getUser().getId();
            voucherOrder.setUserId(userId);
            //6.3 代金券id
            voucherOrder.setVoucherId(voucherId);
            //6.4写入数据库
            save(voucherOrder);
            //7.返回订单id
            return Result.ok(orderId);
    }
}
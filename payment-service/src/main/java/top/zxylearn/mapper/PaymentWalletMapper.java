package top.zxylearn.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import top.zxylearn.entity.PaymentWallet;

import java.math.BigDecimal;

@Mapper
public interface PaymentWalletMapper extends BaseMapper<PaymentWallet> {

    @Update("""
            UPDATE payment_wallet
            SET balance = balance - #{amount}
            WHERE user_id = #{userId}
              AND balance >= #{amount}
            """)
    int deductBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    @Update("""
            UPDATE payment_wallet
            SET balance = balance + #{amount}
            WHERE user_id = #{userId}
            """)
    int addBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}

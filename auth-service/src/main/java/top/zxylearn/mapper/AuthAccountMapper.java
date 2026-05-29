package top.zxylearn.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import top.zxylearn.entity.AuthAccount;

@Mapper
public interface AuthAccountMapper extends BaseMapper<AuthAccount> {
}

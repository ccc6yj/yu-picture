package com.yujian.yupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yujian.yupicturebackend.domain.User;
import com.yujian.yupicturebackend.exception.BusinessException;
import com.yujian.yupicturebackend.exception.ErrorCode;
import com.yujian.yupicturebackend.mapper.UserMapper;
import com.yujian.yupicturebackend.model.dto.user.UserProfileUpdateRequest;
import com.yujian.yupicturebackend.model.dto.user.UserQueryRequest;
import com.yujian.yupicturebackend.model.enums.UserRoleEnum;
import com.yujian.yupicturebackend.model.vo.LoginUserVO;
import com.yujian.yupicturebackend.model.vo.UserVO;
import com.yujian.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.yujian.yupicturebackend.constant.UserConstant.USER_LOGIN_STATE;

/**
* @author liuyujian
* @description 针对表【user(用户)】的数据库操作Service实现
* @createDate 2025-04-21 21:10:58
*/
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    
    private static final String USER_LOGIN_TOKEN_KEY = "user:login:token:";
    private static final long TOKEN_EXPIRE_TIME = 30;
    private static final TimeUnit TOKEN_EXPIRE_UNIT = TimeUnit.MINUTES;

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        // 2. 检查是否重复
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        long count = this.baseMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }
        // 3. 加密
        String encryptPassword = getEncryptPassword(userPassword);
        // 4. 插入数据
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName("无名");
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
        }
        return user.getId();
    }

    @Override
    public String getEncryptPassword(String userPassword) {
        // 盐值，混淆密码
        final String SALT = "yupi";
        return DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        // 2. 加密
        String encryptPassword = getEncryptPassword(userPassword);
        // 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.baseMapper.selectOne(queryWrapper);
        // 用户不存在
        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        
        // 3. 单点登录：清除该用户之前的登录token
        String userIdPrefix = USER_LOGIN_TOKEN_KEY + user.getId() + ":*";
        redisTemplate.delete(redisTemplate.keys(userIdPrefix));
        
        // 4. 生成新的登录token并存储到Redis
        String token = IdUtil.simpleUUID();
        String tokenKey = USER_LOGIN_TOKEN_KEY + user.getId() + ":" + token;
        redisTemplate.opsForValue().set(tokenKey, user, TOKEN_EXPIRE_TIME, TOKEN_EXPIRE_UNIT);
        
        // 5. 将token存储到session中（前端可以从响应头或cookie中获取）
        request.getSession().setAttribute(USER_LOGIN_STATE, token);
        
        // 6. 返回用户信息，同时设置token
        LoginUserVO loginUserVO = this.getLoginUserVO(user);
        loginUserVO.setToken(token);
        return loginUserVO;
    }
    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 从session中获取token
        Object tokenObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        String token = (String) tokenObj;
        
        // 从请求头中获取token（支持前端通过Header传递token）
        if (StrUtil.isBlank(token)) {
            token = request.getHeader("Authorization");
            if (StrUtil.isNotBlank(token) && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
        }
        
        if (StrUtil.isBlank(token)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "未登录");
        }
        
        // 从Redis中查找token对应的用户信息
        String tokenKeyPattern = USER_LOGIN_TOKEN_KEY + "*:" + token;
        java.util.Set<String> keys = redisTemplate.keys(tokenKeyPattern);
        if (keys == null || keys.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "登录已过期，请重新登录");
        }
        
        String tokenKey = keys.iterator().next();
        User currentUser = (User) redisTemplate.opsForValue().get(tokenKey);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "登录已过期，请重新登录");
        }
        
        // 延长token过期时间
        redisTemplate.expire(tokenKey, TOKEN_EXPIRE_TIME, TOKEN_EXPIRE_UNIT);
        
        return currentUser;
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 从session中获取token
        Object tokenObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        String token = (String) tokenObj;
        
        // 从请求头中获取token（支持前端通过Header传递token）
        if (StrUtil.isBlank(token)) {
            token = request.getHeader("Authorization");
            if (StrUtil.isNotBlank(token) && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
        }
        
        if (StrUtil.isBlank(token)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }
        
        // 从Redis中删除token
        String tokenKeyPattern = USER_LOGIN_TOKEN_KEY + "*:" + token;
        java.util.Set<String> keys = redisTemplate.keys(tokenKeyPattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        
        // 移除session中的登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ObjUtil.isNotNull(id), "id", id);
        queryWrapper.eq(StrUtil.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    @Override
    public boolean updateUserProfile(UserProfileUpdateRequest userProfileUpdateRequest, HttpServletRequest request) {
        if (userProfileUpdateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        
        // 获取当前登录用户
        User loginUser = getLoginUser(request);
        Long userId = loginUser.getId();
        
        // 校验参数
        String userName = userProfileUpdateRequest.getUserName();
        String userAvatar = userProfileUpdateRequest.getUserAvatar();
        String userProfile = userProfileUpdateRequest.getUserProfile();
        
        // 校验用户名
        if (StrUtil.isNotBlank(userName)) {
            if (userName.length() > 20) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户昵称过长");
            }
        }
        
        // 校验简介
        if (StrUtil.isNotBlank(userProfile)) {
            if (userProfile.length() > 200) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户简介过长");
            }
        }
        
        // 校验头像URL
        if (StrUtil.isNotBlank(userAvatar)) {
            if (userAvatar.length() > 500) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "头像URL过长");
            }
        }
        
        // 更新用户信息
        User updateUser = new User();
        updateUser.setId(userId);
        if (StrUtil.isNotBlank(userName)) {
            updateUser.setUserName(userName);
        }
        if (StrUtil.isNotBlank(userAvatar)) {
            updateUser.setUserAvatar(userAvatar);
        }
        if (StrUtil.isNotBlank(userProfile)) {
            updateUser.setUserProfile(userProfile);
        }
        
        boolean result = this.updateById(updateUser);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新个人信息失败");
        }
        
        // 更新Redis中的用户信息缓存
        String token = (String) request.getSession().getAttribute(USER_LOGIN_STATE);
        if (StrUtil.isBlank(token)) {
            token = request.getHeader("Authorization");
            if (StrUtil.isNotBlank(token) && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
        }
        
        if (StrUtil.isNotBlank(token)) {
            String tokenKeyPattern = USER_LOGIN_TOKEN_KEY + userId + ":*";
            java.util.Set<String> keys = redisTemplate.keys(tokenKeyPattern);
            if (keys != null && !keys.isEmpty()) {
                String tokenKey = keys.iterator().next();
                // 获取更新后的用户信息
                User updatedUser = this.getById(userId);
                if (updatedUser != null) {
                    // 更新Redis中的用户信息
                    redisTemplate.opsForValue().set(tokenKey, updatedUser, TOKEN_EXPIRE_TIME, TOKEN_EXPIRE_UNIT);
                }
            }
        }
        
        return true;
    }

}





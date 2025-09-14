package com.yujian.yupicturebackend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yujian.yupicturebackend.annotation.AuthCheck;
import com.yujian.yupicturebackend.common.BaseResponse;
import com.yujian.yupicturebackend.common.DeleteRequest;
import com.yujian.yupicturebackend.common.ResultUtils;
import com.yujian.yupicturebackend.domain.User;
import com.yujian.yupicturebackend.exception.BusinessException;
import com.yujian.yupicturebackend.exception.ErrorCode;
import com.yujian.yupicturebackend.exception.ThrowUtils;
import com.yujian.yupicturebackend.constant.UserConstant;
import com.yujian.yupicturebackend.model.dto.user.*;
import com.yujian.yupicturebackend.model.vo.LoginUserVO;
import com.yujian.yupicturebackend.model.vo.UserVO;
import org.springframework.web.multipart.MultipartFile;
import com.yujian.yupicturebackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 用户注册1
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        ThrowUtils.throwIf(userRegisterRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        long result = userService.userRegister(userAccount, userPassword, checkPassword);
        return ResultUtils.success(result);
    }

    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, request);
        return ResultUtils.success(loginUserVO);
    }


    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userService.getLoginUserVO(loginUser));
    }


    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }


    /**
     * 创建用户
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        ThrowUtils.throwIf(userAddRequest == null, ErrorCode.PARAMS_ERROR);
        User user = new User();
        BeanUtils.copyProperties(userAddRequest, user);
        // 默认密码 12345678
        final String DEFAULT_PASSWORD = "12345678";
        String encryptPassword = userService.getEncryptPassword(DEFAULT_PASSWORD);
        user.setUserPassword(encryptPassword);
        boolean result = userService.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(user.getId());
    }

    /**
     * 根据 id 获取用户（仅管理员）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    /**
     * 根据 id 获取包装类
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(long id) {
        BaseResponse<User> response = getUserById(id);
        User user = response.getData();
        return ResultUtils.success(userService.getUserVO(user));
    }

    /**
     * 删除用户
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest,HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 获取当前登录的管理员用户
        User loginUser = userService.getLoginUser(request);
        // 检查是否是删除自己的账户
        if (loginUser.getId().equals(deleteRequest.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "不允许删除自己的账户，请联系其他管理员操作");
        }
        boolean b = userService.removeById(deleteRequest.getId());
        return ResultUtils.success(b);
    }

    /**
     * 更新用户
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest) {
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtils.copyProperties(userUpdateRequest, user);
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 分页获取用户封装列表（仅管理员）
     *
     * @param userQueryRequest 查询请求参数
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long current = userQueryRequest.getCurrent();
        long pageSize = userQueryRequest.getPageSize();
        Page<User> userPage = userService.page(new Page<>(current, pageSize),
                userService.getQueryWrapper(userQueryRequest));
        Page<UserVO> userVOPage = new Page<>(current, pageSize, userPage.getTotal());
        List<UserVO> userVOList = userService.getUserVOList(userPage.getRecords());
        userVOPage.setRecords(userVOList);
        return ResultUtils.success(userVOPage);
    }

    /**
     * 更新个人信息
     */
    @PostMapping("/update/profile")
    public BaseResponse<Boolean> updateUserProfile(@RequestBody UserProfileUpdateRequest userProfileUpdateRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(userProfileUpdateRequest == null, ErrorCode.PARAMS_ERROR);
        boolean result = userService.updateUserProfile(userProfileUpdateRequest, request);
        return ResultUtils.success(result);
    }

    /**
     * 上传用户头像
     */
    @PostMapping("/upload/avatar")
    public BaseResponse<String> uploadAvatar(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "文件不能为空");
        
        // 校验文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持图片文件");
        }
        
        // 校验文件大小（2MB）
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过2MB");
        }
        
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        
        try {
            // 生成文件名
            String originalFilename = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String fileName = "avatar_" + loginUser.getId() + "_" + System.currentTimeMillis() + fileExtension;
            
            // 创建上传目录
            String uploadDir = System.getProperty("user.dir") + "/uploads/avatars/";
            java.io.File dir = new java.io.File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            // 保存文件
            java.io.File destFile = new java.io.File(dir, fileName);
            file.transferTo(destFile);
            
            // 返回文件访问URL
            String avatarUrl = "/uploads/avatars/" + fileName;
            
            // 更新用户头像URL
            UserProfileUpdateRequest updateRequest = new UserProfileUpdateRequest();
            updateRequest.setUserAvatar(avatarUrl);
            userService.updateUserProfile(updateRequest, request);
            
            return ResultUtils.success(avatarUrl);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件上传失败：" + e.getMessage());
        }
    }

}

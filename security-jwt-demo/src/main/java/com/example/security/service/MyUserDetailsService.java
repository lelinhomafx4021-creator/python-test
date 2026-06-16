package com.example.security.service;

import com.example.security.entity.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 用户DetailsService（模拟数据库）
 */
@Service
public class MyUserDetailsService implements UserDetailsService {

    // 模拟数据库（实际项目从数据库查）
    private static final Map<String, User> USER_DB = Map.of(
            "zhangsan", new User(1L, "zhangsan", "$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi", "ADMIN"),
            "lisi", new User(2L, "lisi", "$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi", "USER")
    );

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 从"数据库"查用户
        User user = USER_DB.get(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        // 返回 UserDetails
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
        );
    }
}

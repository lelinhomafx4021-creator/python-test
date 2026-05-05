package com.aiinvestor.gateway.modules.identity.service;

import com.aiinvestor.gateway.modules.identity.dao.entity.UserDO;
import com.aiinvestor.gateway.modules.identity.dao.mapper.UserMapper;
import com.aiinvestor.gateway.modules.identity.dto.RegisterRequest;
import com.aiinvestor.gateway.modules.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private UserService userService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private UserDO sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new UserDO();
        sampleUser.setId(1L);
        sampleUser.setUsername("testuser");
        sampleUser.setPasswordHash(passwordEncoder.encode("correctPassword"));
        sampleUser.setPhone("13800138000");
        sampleUser.setEmail("test@example.com");
        sampleUser.setNickname("tester");
        sampleUser.setRole("normal");
        sampleUser.setStatus("1");
    }

    @Nested
    @DisplayName("validateLogin")
    class ValidateLogin {

        @Test
        void shouldLoginSuccessfully() {
            when(userMapper.selectOne(any())).thenReturn(sampleUser);
            when(userMapper.updateById(any(UserDO.class))).thenReturn(1);

            UserDO result = userService.validateLogin("testuser", "correctPassword");

            assertNotNull(result);
            assertEquals("testuser", result.getUsername());
            verify(userMapper).updateById(any(UserDO.class));
        }

        @Test
        void shouldReturnNullWhenUserNotFound() {
            when(userMapper.selectOne(any())).thenReturn(null);

            UserDO result = userService.validateLogin("nobody", "anyPassword");

            assertNull(result);
            verify(userMapper, never()).updateById(any(UserDO.class));
        }

        @Test
        void shouldReturnNullWhenInactive() {
            sampleUser.setStatus("0");
            when(userMapper.selectOne(any())).thenReturn(sampleUser);

            UserDO result = userService.validateLogin("testuser", "correctPassword");

            assertNull(result);
            verify(userMapper, never()).updateById(any(UserDO.class));
        }

        @Test
        void shouldReturnNullWhenPasswordMismatch() {
            when(userMapper.selectOne(any())).thenReturn(sampleUser);

            UserDO result = userService.validateLogin("testuser", "wrongPassword");

            assertNull(result);
            verify(userMapper, never()).updateById(any(UserDO.class));
        }
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        void shouldRegisterSuccessfully() {
            when(userMapper.selectOne(any())).thenReturn(null);
            when(userMapper.insert(any(UserDO.class))).thenReturn(1);

            RegisterRequest request = buildRequest();
            UserDO result = userService.register(request);

            assertNotNull(result);
            assertEquals("newuser", result.getUsername());
            assertEquals("newuser@example.com", result.getEmail());
            assertEquals("normal", result.getRole());
            assertEquals("1", result.getStatus());
            assertNotNull(result.getPasswordHash());
            verify(emailVerificationService).verifyRegisterCode("newuser@example.com", "123456");
            verify(userMapper).insert(any(UserDO.class));
        }

        @Test
        void shouldThrowWhenDuplicateUsername() {
            when(userMapper.selectOne(any())).thenReturn(sampleUser);

            RegisterRequest request = buildRequest();

            assertThrows(BusinessException.class, () -> userService.register(request));
            verify(emailVerificationService, never()).verifyRegisterCode(any(), any());
            verify(userMapper, never()).insert(any(UserDO.class));
        }

        @Test
        void shouldUseUsernameAsDefaultNickname() {
            when(userMapper.selectOne(any())).thenReturn(null);
            when(userMapper.insert(any(UserDO.class))).thenReturn(1);

            RegisterRequest request = buildRequest();
            request.setNickname(null);

            UserDO result = userService.register(request);

            assertEquals("newuser", result.getNickname());
        }

        @Test
        void shouldSetPhoneNullWhenBlank() {
            when(userMapper.selectOne(any())).thenReturn(null);
            when(userMapper.insert(any(UserDO.class))).thenReturn(1);

            RegisterRequest request = buildRequest();
            request.setPhone("");

            UserDO result = userService.register(request);

            assertNull(result.getPhone());
        }
    }

    @Nested
    @DisplayName("sendRegisterEmailCode")
    class SendRegisterEmailCode {

        @Test
        void shouldSendVerificationCodeForNewEmail() {
            when(userMapper.selectOne(any())).thenReturn(null);

            userService.sendRegisterEmailCode("NewUser@Example.com");

            verify(emailVerificationService).sendRegisterCode("newuser@example.com");
        }

        @Test
        void shouldRejectRegisteredEmail() {
            when(userMapper.selectOne(any())).thenReturn(sampleUser);

            assertThrows(BusinessException.class, () -> userService.sendRegisterEmailCode("test@example.com"));
            verify(emailVerificationService, never()).sendRegisterCode(any());
        }
    }

    private RegisterRequest buildRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("password123");
        request.setNickname("newbie");
        request.setPhone("13900139000");
        request.setEmail("NewUser@Example.com");
        request.setEmailCode("123456");
        return request;
    }
}

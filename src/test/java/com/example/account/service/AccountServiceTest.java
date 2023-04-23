package com.example.account.service;

import com.example.account.domain.Account;
import com.example.account.domain.AccountUser;
import com.example.account.dto.AccountDto;
import com.example.account.exception.AccountException;
import com.example.account.repository.AccountUserRepository;
import com.example.account.type.AccountStatus;
import com.example.account.repository.AccountRepository;
import com.example.account.type.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AccountUserRepository accountUserRepository;
    @InjectMocks
    private AccountService accountService;

    @Test
    void createAccountSuccess(){
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi").build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));
        given(accountRepository.findFirstByOrderByIdDesc())
                .willReturn(Optional.of(Account.builder()
                                .accountUser(user)
                        .accountNumber("1000000001").build()));
        given(accountRepository.save(any()))
                .willReturn(Account.builder()
                        .accountUser(user)
                        .accountNumber("1000000002").build());

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);

        AccountDto accountDto = accountService.createAccount(1L, 1000L);

        verify(accountRepository,times(1)).save(captor.capture());
        assertEquals(12L,accountDto.getUserId());
        assertEquals("1000000002",captor.getValue().getAccountNumber());
    }

    @Test
    void createFirstAccount(){
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi").build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));
        given(accountRepository.findFirstByOrderByIdDesc())
                .willReturn(Optional.empty());
        given(accountRepository.save(any()))
                .willReturn(Account.builder()
                        .accountUser(user)
                        .accountNumber("1000000002").build());

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);

        AccountDto accountDto = accountService.createAccount(1L, 1000L);

        verify(accountRepository,times(1)).save(captor.capture());
        assertEquals(12L,accountDto.getUserId());
        assertEquals("1000000000",captor.getValue().getAccountNumber());
    }


    @Test
    @DisplayName("해당 유저 없음 - 계좌 생성 실패")
    void createAccount_UserNotFound(){
        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.empty());

        AccountException accountException = assertThrows(AccountException.class,
                () -> accountService.createAccount(1L, 1000L));

        assertEquals(ErrorCode.USER_NOT_FOUND, accountException.getErrorCode());
    }

    @Test
    @DisplayName("유저당 최대 계좌는 10개")
    void createAccount_maxAccountIs10(){
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi").build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));
        given(accountRepository.countAccountByUser(any()))
                .willReturn(10);


        AccountException accountException = assertThrows(AccountException.class,
                () -> accountService.createAccount(1L, 1000L));


        assertEquals(ErrorCode.MAX_ACCOUNT_PER_USER_10, accountException.getErrorCode());
    }

    @Test
    void deleteAccountSuccess(){
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi").build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(Account.builder()
                        .accountUser(user)
                        .balance(0L)
                        .accountNumber("4567891230").build()));

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);

        AccountDto accountDto = accountService.deleteAccount(1L, "1234567890");

        verify(accountRepository,times(1)).save(captor.capture());
        assertEquals(12L,accountDto.getUserId());
        assertEquals("4567891230",captor.getValue().getAccountNumber());
        assertEquals(AccountStatus.UNREGISTERED,captor.getValue().getAccountStatus());
    }

    @Test
    @DisplayName("해당 유저 없음 - 계좌 해지 실패")
    void deleteAccount_UserNotFound(){
        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.empty());

        AccountException accountException = assertThrows(AccountException.class,
                () -> accountService.deleteAccount(1L, "1234567890"));

        assertEquals(ErrorCode.USER_NOT_FOUND, accountException.getErrorCode());
    }

    @Test
    @DisplayName("해당 계좌 없음 - 계좌 해지 실패")
    void deleteAccount_AccountNotFound(){
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi").build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.empty());

        AccountException accountException = assertThrows(AccountException.class,
                () -> accountService.deleteAccount(1L, "1234567890"));

        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, accountException.getErrorCode());
    }

    @Test
    @DisplayName("계좌 소유주 다름")
    void deleteAccountFailed_userUnMathch(){
        AccountUser pobi = AccountUser.builder()
                .id(12L)
                .name("Pobi").build();
        AccountUser harry = AccountUser.builder()
                .id(13L)
                .name("Harry").build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(pobi));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(Account.builder()
                        .accountUser(harry)
                        .balance(0L)
                        .accountNumber("1000000000").build()));

        AccountException accountException = assertThrows(AccountException.class,
                () -> accountService.deleteAccount(1L, "1234567890"));

        assertEquals(ErrorCode.USER_ACCOUNT_UN_MATH, accountException.getErrorCode());
    }

    @Test
    @DisplayName("해지 계좌는 잔액이 없어야 한다")
    void deleteAccountFailed_balanceNotEmpty(){
        AccountUser pobi = AccountUser.builder()
                .id(12L)
                .name("Pobi").build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(pobi));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(Account.builder()
                        .accountUser(pobi)
                        .balance(100L)
                        .accountNumber("1000000000").build()));

        AccountException accountException = assertThrows(AccountException.class,
                () -> accountService.deleteAccount(1L, "1234567890"));

        assertEquals(ErrorCode.BALANCE_NOT_EMPTY, accountException.getErrorCode());
    }

    @Test
    @DisplayName("해지 계좌는 해지할 수 없다")
    void deleteAccountFailed_alreadyUnregistered(){
        AccountUser pobi = AccountUser.builder()
                .id(12L)
                .name("Pobi").build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(pobi));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(Account.builder()
                        .accountUser(pobi)
                        .accountStatus(AccountStatus.UNREGISTERED)
                        .balance(0L)
                        .accountNumber("1000000000").build()));

        AccountException accountException = assertThrows(AccountException.class,
                () -> accountService.deleteAccount(1L, "1234567890"));

        assertEquals(ErrorCode.ACCOUNT_ALREADY_UNREGISTERED, accountException.getErrorCode());
    }

}
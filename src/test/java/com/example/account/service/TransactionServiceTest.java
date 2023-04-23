package com.example.account.service;

import com.example.account.domain.Account;
import com.example.account.domain.AccountUser;
import com.example.account.domain.Transaction;
import com.example.account.dto.TransactionDto;
import com.example.account.exception.AccountException;
import com.example.account.repository.AccountRepository;
import com.example.account.repository.AccountUserRepository;
import com.example.account.repository.TransactionRepository;
import com.example.account.type.AccountStatus;
import com.example.account.type.ErrorCode;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.example.account.type.TransactionResultType.F;
import static com.example.account.type.TransactionResultType.S;
import static com.example.account.type.TransactionType.CANCEL;
import static com.example.account.type.TransactionType.USE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)

class TransactionServiceTest {
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AccountUserRepository accountUserRepository;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void successUseBalance(){
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi").build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));
        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(AccountStatus.IN_USE)
                .balance(10000L)
                .accountNumber("1000000012").build();
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));
        given(transactionRepository.save(any()))
                .willReturn(Transaction.builder()
                        .account(account)
                        .trasactionType(USE)
                        .transactionResultType(S)
                        .transactionId("transactionId")
                        .amount(1000L)
                        .balanceSnapshot(9000L)
                        .build());
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

        TransactionDto transactionDto = transactionService.useBalance(
                1L, "1000000000", 200L);

        verify(transactionRepository, times(1)).save(captor.capture());
        assertEquals(200L,captor.getValue().getAmount());
        assertEquals(9800L,captor.getValue().getBalanceSnapshot());
        assertEquals(S, transactionDto.getTransactionResultType());
        assertEquals(USE, transactionDto.getTrasactionType());
        assertEquals(9000L, transactionDto.getBalanceSnapshot());
        assertEquals(1000L, transactionDto.getAmount());
    }

    @Test
    @DisplayName("해당 유저 없음 - 계좌 생성 실패")
    void useBalance_UserNotFound(){
        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.empty());

        AccountException accountException = assertThrows(AccountException.class,
                () -> transactionService.useBalance(1L, "1000000000",1000L));

        assertEquals(ErrorCode.USER_NOT_FOUND, accountException.getErrorCode());
    }

    @Test
    @DisplayName("해당 계좌 없음 - 계좌 해지 실패")
    void useBalance_AccountNotFound(){
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi").build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.empty());

        AccountException accountException = assertThrows(AccountException.class,
                () -> transactionService.useBalance(1L, "1000000000",1000L));

        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, accountException.getErrorCode());
    }

    @Test
    @DisplayName("계좌 소유주 다름")
    void useBalanceFailed_userUnMathch(){
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
                () -> transactionService.useBalance(1L, "1234657890",1000L));

        assertEquals(ErrorCode.USER_ACCOUNT_UN_MATH, accountException.getErrorCode());
    }

    @Test
    @DisplayName("해지 계좌는 사용할 수 없다")
    void useBalanceFailed_alreadyUnregistered(){
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
                () -> transactionService.useBalance(1L, "1234657890",1000L));

        assertEquals(ErrorCode.ACCOUNT_ALREADY_UNREGISTERED, accountException.getErrorCode());
    }


    @Test
    @DisplayName("거래 금액이 잔액보다 클 경우")
    void exceedAmount_UseBalance(){
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi").build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));
        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(AccountStatus.IN_USE)
                .balance(100L)
                .accountNumber("1000000012").build();
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));

        AccountException accountException = assertThrows(AccountException.class,
                () -> transactionService.useBalance(1L, "1234657890",1000L));

        assertEquals(ErrorCode.AMOUNT_EXCEED_BALANCE, accountException.getErrorCode());
        verify(transactionRepository, times(0)).save(any());
    }

    @Test
    @DisplayName("실패 트랜잭션 저장 성공")
    void saveFailedUseBalance(){
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi").build();
        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(AccountStatus.IN_USE)
                .balance(10000L)
                .accountNumber("1000000012").build();
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));
        given(transactionRepository.save(any()))
                .willReturn(Transaction.builder()
                        .account(account)
                        .trasactionType(USE)
                        .transactionResultType(S)
                        .transactionId("transactionId")
                        .amount(1000L)
                        .balanceSnapshot(9000L)
                        .build());
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

        transactionService.saveFailedUseTransaction("1000000000",200L);

        verify(transactionRepository, times(1)).save(captor.capture());
        assertEquals(200L,captor.getValue().getAmount());
        assertEquals(10000L,captor.getValue().getBalanceSnapshot());
        assertEquals(F, captor.getValue().getTransactionResultType());
    }

    @Test
    void successCancelBalance(){
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi").build();
        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(AccountStatus.IN_USE)
                .balance(10000L)
                .accountNumber("1000000012").build();
        Transaction transaction = Transaction.builder()
                .account(account)
                .trasactionType(USE)
                .transactionResultType(S)
                .transactionId("transactionId")
                .transactedAt(LocalDateTime.now())
                .amount(1000L)
                .balanceSnapshot(9000L)
                .build();
        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(transaction));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));
        given(transactionRepository.save(any()))
                .willReturn(Transaction.builder()
                        .account(account)
                        .trasactionType(CANCEL)
                        .transactionResultType(S)
                        .transactionId("transactionIdCancel")
                        .transactedAt(LocalDateTime.now())
                        .amount(1000L)
                        .balanceSnapshot(10000L)
                        .build());
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

        TransactionDto transactionDto = transactionService.cancelBalance(
                "transactionId", "1000000000", 1000L);

        verify(transactionRepository, times(1)).save(captor.capture());
        assertEquals(1000L,captor.getValue().getAmount());
        assertEquals(10000L + 1000L,captor.getValue().getBalanceSnapshot());
        assertEquals(S, transactionDto.getTransactionResultType());
        assertEquals(CANCEL, transactionDto.getTrasactionType());
        assertEquals(10000L, transactionDto.getBalanceSnapshot());
        assertEquals(1000L, transactionDto.getAmount());
    }

    @Test
    @DisplayName("해당 계좌 없음 - 계좌 사용 취소 실패")
    void cancelBalance_AccountNotFound(){


        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(Transaction.builder().build()));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.empty());

        AccountException accountException = assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("transaction", "1000000000",1000L));

        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, accountException.getErrorCode());
    }

    @Test
    @DisplayName("사용 거래 없음 - 잔액 사용 취소 실패")
    void cancelBalance_TransactionNotFound(){


        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.empty());

        AccountException accountException = assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("transaction", "1000000000",1000L));

        assertEquals(ErrorCode.TRANSACTION_NOT_FOUND, accountException.getErrorCode());
    }

    @Test
    @DisplayName("거래와 계좌가 매칭 실패 - 잔액 사용 취소 실패")
    void cancelBalance_TransactionAccountUnMatch(){
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi").build();
        Account account = Account.builder()
                .id(1L)
                .accountUser(user)
                .accountStatus(AccountStatus.IN_USE)
                .balance(10000L)
                .accountNumber("1000000012").build();
        Account accountNotUse = Account.builder()
                .id(2L)
                .accountUser(user)
                .accountStatus(AccountStatus.IN_USE)
                .balance(10000L)
                .accountNumber("1000000013").build();
        Transaction transaction = Transaction.builder()
                .account(account)
                .trasactionType(USE)
                .transactionResultType(S)
                .transactionId("transactionId")
                .transactedAt(LocalDateTime.now())
                .amount(1000L)
                .balanceSnapshot(9000L)
                .build();

        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(transaction));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(accountNotUse));

        AccountException accountException = assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("transactionId",
                        "1000000000",
                        1000L));

        assertEquals(ErrorCode.TRANSACTION_ACCOUNT_UN_MATCH, accountException.getErrorCode());
    }

    @Test
    @DisplayName("거래금액과 취소금액이 다름 - 잔액 사용 취소 실패")
    void cancelBalance_CancelMustFully(){
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi").build();
        Account account = Account.builder()
                .id(1L)
                .accountUser(user)
                .accountStatus(AccountStatus.IN_USE)
                .balance(10000L)
                .accountNumber("1000000012").build();
        Transaction transaction = Transaction.builder()
                .account(account)
                .trasactionType(USE)
                .transactionResultType(S)
                .transactionId("transactionId")
                .transactedAt(LocalDateTime.now())
                .amount(1000L + 1000L)
                .balanceSnapshot(9000L)
                .build();

        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(transaction));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));

        AccountException accountException = assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("transactionId",
                        "1000000000",
                        1000L));

        assertEquals(ErrorCode.CANCEL_MUST_FULLY, accountException.getErrorCode());
    }

    @Test
    @DisplayName("취소는 1년까지만 가능 - 잔액 사용 취소 실패")
    void cancelBalance_TooOldOrder(){
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi").build();
        Account account = Account.builder()
                .id(1L)
                .accountUser(user)
                .accountStatus(AccountStatus.IN_USE)
                .balance(10000L)
                .accountNumber("1000000012").build();
        Transaction transaction = Transaction.builder()
                .account(account)
                .trasactionType(USE)
                .transactionResultType(S)
                .transactionId("transactionId")
                .transactedAt(LocalDateTime.now().minusYears(1).minusDays(1))
                .amount(1000L)
                .balanceSnapshot(9000L)
                .build();

        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(transaction));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));

        AccountException accountException = assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("transactionId",
                        "1000000000",
                        1000L));

        assertEquals(ErrorCode.TOO_OLD_ORDER_TO_CANCEL, accountException.getErrorCode());
    }

}
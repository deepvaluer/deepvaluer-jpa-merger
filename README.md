# deepvaluer-jpa-merger

`deepvaluer-jpa-merger`는 Spring Data JPA를 위한 확장 라이브러리로, 강력하고 세밀하게 구성 가능한 엔티티 병합(Merge) 기능을 제공합니다. 기존 엔티티를 업데이트하거나 새로운 엔티티를 삽입할 때, 특정 필드의 병합을 제외하거나 `null` 값으로 덮어쓰도록 허용하는 등 필드 수준의 세밀한 제어가 가능하며, 자연키(Natural ID) 또는 기본키(Primary Key)를 기반으로 작동합니다.

## 주요 기능
- **선언적 병합 정책**: `@MergePolicies`와 `@MergePolicy`를 사용하여 여러 병합 동작을 정의할 수 있습니다.
- **필드 단위 세밀한 제어**: `@MergeField` 애노테이션을 사용하여 각 필드별 병합 동작을 제어합니다.
- **Natural ID 지원**: 기본키(PK)를 확인하기 전에 하나 이상의 자연 식별자(Natural Identifier)를 기준으로 단일 엔티티 또는 목록을 매칭하여 매끄럽게 병합합니다.
- **리포지토리 통합 지원**: 확장 리포지토리인 `MergeableJpaRepository`가 조회, 충돌 감지, 병합 또는 삽입(Upsert) 로직을 매끄럽게 처리합니다.
- **명확한 예외 처리**: `MergeException` 하나로 통일되었으며, `MergeErrorCode`를 통해 중복 엔티티, 검증 실패, 엔티티 미발견 등 상황에 맞는 구체적인 에러 코드를 제공합니다.

## 빠른 시작 (Quick Start)
병합 기능을 사용하려면 엔티티가 `Mergeable<T>` 인터페이스를 구현해야 하며, 일치하는 정책(Policy)이나 필드 레벨 애노테이션을 선언해야 합니다.

### 1. 엔티티 구현 (Entity Implementation)
새로 들어오는 엔티티가 기존 엔티티에 병합될 때 필드들이 어떻게 처리될지 선언합니다.

```java
import io.deepvaluer.jpa.merger.domain.Mergeable;
import io.deepvaluer.jpa.merger.annotation.MergeField;

@Entity
@Table(name = "users")
@Data
@DynamicUpdate
public class UserEntity implements Mergeable<UserEntity> {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    // 병합을 진행할 때, 기존 발급된 ID 값을 덮어쓰지 않도록 제외시킵니다.
    @MergeField(exclude = true)
    private Long id;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "email")
    private String email;

    @Column(name = "name")
    private String name;

    // 기본적으로 새로 들어오는 필드의 값이 null이면, 기존 필드의 값이 유지됩니다.
    // 하지만 닉네임 필드의 경우 명시적으로 null 값으로 덮어쓰는 것(삭제)을 허용할 수 있습니다.
    @Column(name = "nickname")
    @MergeField(overwriteNull = true)
    private String nickname;
    
    // ... 기타 필드 생략
}
```

### 2. 리포지토리 설정 (Repository Setup)
Spring Data JPA 리포지토리 인터페이스가 `MergeableJpaRepository`를 상속(extends) 받도록 설정하여 병합 메서드를 사용할 수 있게 합니다.

```java
import io.deepvaluer.jpa.merger.repository.MergeableJpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends MergeableJpaRepository<UserEntity, Long> {
}
```

### 3. 서비스 계층 적용 (Usage inside a Service)

```java
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public void upsertUsers(List<UserEntity> incomingUsers) {
        
        // mergeAll(entities, throwIfExists, ID property name, natural ID columns... )
        //
        // 다음 과정이 수행됩니다:
        // 1. 조합된 Natural ID (tenantId, email)와 일치하는 기존 엔티티를 조회합니다.
        // 2. 존재하는 엔티티일 경우 -> @MergeField 우선순위 및 정책을 기반으로 프로퍼티를 병합합니다.
        // 3. 존재하지 않는 엔티티일 경우 -> 새로 삽입(Insert)합니다.
        // 4. 마지막으로 모든 엔티티들을 일괄 저장(Save)합니다.
        
        userRepository.mergeAll(
            incomingUsers, 
            false,       // existsThrow: false일 경우 병합 허용. true일 경우 이미 존재하면 예외 발생.
            "id",        // 기본키(PK) 프로퍼티 명
            "tenantId", "email" // 복합 자연키(Natural Key) 역할을 할 필드명 입력
        );
    }
}
```

## 예외 처리 (Exception Handling)
라이브러리의 병합 규칙 위반 시 `io.deepvaluer.jpa.merger.exception.MergeException`이 발생합니다. `.getErrorCode()` 메서드를 통해 `MergeErrorCode` Enum 값을 확인하여 구체적인 실패 원인을 파악할 수 있습니다:

- `DUPLICATE_ENTITY`: 입력 목록에 Natural ID가 중복된 엔티티가 존재하거나, PK 충돌이 감지되었습니다.
- `VALIDATION_FAILED`: 식별자 등 필수 프로퍼티 검증에 실패했습니다. (예: 필수 값이 null)
- `ENTITY_NOT_FOUND`: 엄격한 조회 옵션에서 매칭되는 엔티티 ID를 찾지 못했습니다.
- `POLICY_NOT_FOUND`: 엔티티나 메서드 레벨에 지정된 `@MergePolicy` 정책을 찾을 수 없습니다.
- `ACCESS_ERROR`: Reflection(리플렉션)을 통해 엔티티의 필드 값을 연결/복사하는 과정에서 오류가 발생했습니다.

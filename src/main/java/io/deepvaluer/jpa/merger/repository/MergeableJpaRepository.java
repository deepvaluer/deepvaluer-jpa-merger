package io.deepvaluer.jpa.merger.repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.criteria.Predicate;

import org.springframework.beans.BeanWrapperImpl;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import io.deepvaluer.jpa.merger.domain.Mergeable;
import io.deepvaluer.jpa.merger.exception.MergeErrorCode;
import io.deepvaluer.jpa.merger.exception.MergeException;

@NoRepositoryBean
public interface MergeableJpaRepository<T, ID>
        extends JpaRepository<T, ID>, JpaSpecificationExecutor<T> {

    /**
     * 여러 프로퍼티-값 매핑을 AND 로 묶은 뒤, 각 매핑 세트를 OR 로 결합해 조회.
     * 
     * 예: [{a:1, b:2}, {a:3, c:4}]
     * -> (a=1 AND b=2) OR (a=3 AND c=4)
     *
     * @param propValueSets 각 Map 은 "필드명"→"검색값" 쌍의 묶음
     */
    default List<T> findAllByPropertySets(List<Map<String, Object>> propValueSets) {
        // 전체 OR 스펙
        Specification<T> orSpec = null;

        for (Map<String, Object> props : propValueSets) {
            // 이 Map 내의 모든 조건을 AND 로 묶는 스펙
            Specification<T> andSpec = (root, query, cb) -> {
                Predicate p = null;
                for (Map.Entry<String, Object> entry : props.entrySet()) {
                    Predicate eq = cb.equal(root.get(entry.getKey()), entry.getValue());
                    p = (p == null ? eq : cb.and(p, eq));
                }
                return p;
            };
            orSpec = (orSpec == null ? andSpec : orSpec.or(andSpec));
        }

        // 조건이 하나도 없으면 전체 조회
        if (orSpec == null) {
            return findAll();
        }
        return findAll(orSpec);
    }

    default List<T> findAllByPropertySets(Map<String, Object> propValueSets) {
        return findAllByPropertySets(List.of(propValueSets));
    }

    /**
     * 엔티티를 ID 로 매칭해 병합 후 저장.
     *
     * @param entities    저장·병합할 엔티티
     * @param existsThrow 이미 있을 때 예외 던질지 여부
     * @param idPropName  ID 프로퍼티 명
     */
    default List<T> mergeAllById(List<T> entities,
            boolean existsThrow,
            String idPropName) {
        return mergeAllByIdWithPolicy(entities, existsThrow, idPropName, null);
    }

    /**
     * 엔티티를 ID 로 매칭해 병합 후 저장. (정책 지정)
     *
     * @param entities    저장·병합할 엔티티
     * @param existsThrow 이미 있을 때 예외 던질지 여부
     * @param idPropName  ID 프로퍼티 명
     * @param policyName  사용할 MergePolicy 이름 (null이면 기본 병합)
     */
    default List<T> mergeAllByIdWithPolicy(List<T> entities,
            boolean existsThrow,
            String idPropName,
            String policyName) {

        List<T> workEntities = new ArrayList<>(entities);
        List<Map<String, Object>> idLookups = new ArrayList<>();
        // ─── 입력 내 id 중복 체크 ───
        Set<String> seen = new HashSet<>();
        for (T e : workEntities) {
            BeanWrapperImpl w = new BeanWrapperImpl(e);
            Object idVal = w.getPropertyValue(idPropName);
            if (idVal == null) {
                continue;
            }
            String signature = idPropName + "=" + Objects.toString(idVal, "");
            if (!seen.add(signature)) {
                throw new MergeException(MergeErrorCode.DUPLICATE_ENTITY,
                        "Duplicate natural ID in input list: " + signature);
            } else {
                idLookups.add(Map.of(idPropName, idVal));
            }
        }
        List<T> foundLookups = findAllByPropertySets(idLookups);
        Map<Object, T> foundMap = foundLookups.stream()
                .collect(Collectors.toMap(
                        e -> {
                            BeanWrapperImpl w = new BeanWrapperImpl(e);
                            return w.getPropertyValue(idPropName);
                        },
                        e -> e));

        for (int i = 0; i < workEntities.size(); i++) {
            T incoming = workEntities.get(i);
            BeanWrapperImpl incomingWrap = new BeanWrapperImpl(incoming);
            Object idVal = incomingWrap.getPropertyValue(idPropName);

            T existing;
            if (idVal != null) {
                // ID 값이 있으면 조회
                T found = foundMap.get(idVal);
                if (found != null) {
                    existing = found;
                } else {
                    throw new MergeException(MergeErrorCode.ENTITY_NOT_FOUND,
                            "No entity found with ID: " + idVal);
                }
            } else {
                existing = incoming;
            }

            // Mergeable 구현체면 merge, 아니면 incoming에 기존 ID 세팅
            T toSave;
            if (existing instanceof Mergeable<?>) {
                @SuppressWarnings("unchecked")
                Mergeable<T> m = (Mergeable<T>) existing;
                if (policyName != null) {
                    m.mergeWithPolicy(incoming, policyName);
                } else {
                    m.mergeAllFields(incoming);
                }
                toSave = (T) m;
            } else {
                toSave = incoming;
            }

            workEntities.set(i, toSave);
        }

        return saveAllAndFlush(workEntities);
    }

    default String signature(T entity, String... naturalIdPropNames) {
        return signature(entity, null, naturalIdPropNames);
    }

    default String signature(T entity, Map<String, Object> property, String... naturalIdPropNames) {
        BeanWrapperImpl w = new BeanWrapperImpl(entity);
        // key1=val1|key2=val2 ... 형태로 문자열 생성
        String signature = Arrays.stream(naturalIdPropNames)
                .map(prop -> {
                    Object v = w.getPropertyValue(prop);
                    if (v == null
                            || (v instanceof String && ((String) v).trim().isEmpty())) {
                        throw new MergeException(MergeErrorCode.VALIDATION_FAILED,
                                "ID property '" + prop +
                                        "' cannot be null or empty for entity: " + entity);
                    }
                    if (property != null) {
                        property.put(prop, v);
                    }
                    return prop + "=" + Objects.toString(v, "");
                })
                .collect(Collectors.joining("|"));
        return signature;
    }

    /**
     * 엔티티를 natural-id 로 매칭해 병합 후 저장.
     *
     * @param entities           저장·병합할 엔티티들
     * @param existsThrow        이미 있을 때 예외 던질지 여부
     * @param idPropName         ID 프로퍼티 명
     * @param naturalIdPropNames 자연키 프로퍼티 명들
     */
    default List<T> mergeAll(List<T> entities,
            boolean existsThrow,
            String idPropName,
            String... naturalIdPropNames) {
        return mergeAllWithPolicy(entities, existsThrow, idPropName, null, naturalIdPropNames);
    }

    /**
     * 엔티티를 natural-id 로 매칭해 병합 후 저장. (정책 지정)
     *
     * @param entities           저장·병합할 엔티티들
     * @param existsThrow        이미 있을 때 예외 던질지 여부
     * @param idPropName         ID 프로퍼티 명
     * @param policyName         사용할 MergePolicy 이름 (null이면 기본 병합)
     * @param naturalIdPropNames 자연키 프로퍼티 명들
     */
    default List<T> mergeAllWithPolicy(List<T> entities,
            boolean existsThrow,
            String idPropName,
            String policyName,
            String... naturalIdPropNames) {

        List<T> workEntities = new ArrayList<>(entities);
        List<Map<String, Object>> idLookups = new java.util.ArrayList<>();

        // ─── 입력 내 자연키 중복 체크 ───
        Set<String> seen = new HashSet<>();
        for (T e : workEntities) {
            Map<String, Object> lookup = new HashMap<>();
            // key1=val1|key2=val2 ... 형태로 문자열 생성
            String signature = signature(e, lookup, naturalIdPropNames);
            if (!seen.add(signature)) {
                throw new MergeException(MergeErrorCode.DUPLICATE_ENTITY,
                        "Duplicate natural ID in input list: " + signature);
            } else {
                idLookups.add(lookup);
            }
        }

        List<T> foundLookups = findAllByPropertySets(idLookups);
        Map<Object, T> foundMap = foundLookups.stream()
                .collect(Collectors.toMap(
                        e -> signature(e, naturalIdPropNames),
                        e -> e));

        for (int i = 0; i < workEntities.size(); i++) {
            T incoming = workEntities.get(i);
            BeanWrapperImpl incomingWrap = new BeanWrapperImpl(incoming);
            String signature = signature(incoming, naturalIdPropNames);

            // OR-AND 조합 조회
            T found = foundMap.get(signature);
            if (found != null) {
                T existing = found;
                BeanWrapperImpl existWrap = new BeanWrapperImpl(existing);

                // ID 충돌 체크
                Object existingId = existWrap.getPropertyValue(idPropName);
                Object incomingId = incomingWrap.getPropertyValue(idPropName);
                if (existsThrow && (incomingId == null || !incomingId.equals(existingId))) {
                    throw new MergeException(MergeErrorCode.DUPLICATE_ENTITY,
                            "Entity already exists with natural ID: " + signature);
                }

                // Mergeable 구현체면 merge, 아니면 incoming에 기존 ID 세팅
                T toSave;
                if (existing instanceof Mergeable<?>) {
                    @SuppressWarnings("unchecked")
                    Mergeable<T> m = (Mergeable<T>) existing;
                    if (policyName != null) {
                        m.mergeWithPolicy(incoming, policyName);
                    } else {
                        m.mergeAllFields(incoming);
                    }
                    toSave = (T) m;
                } else {
                    incomingWrap.setPropertyValue(idPropName, existingId);
                    toSave = incoming;
                }

                workEntities.set(i, toSave);
            }
        }

        return saveAllAndFlush(workEntities);
    }

    /**
     * 엔티티를 natural-id 로 매칭해 병합 후 저장.
     *
     * @param entity             저장·병합할 엔티티
     * @param existsThrow        이미 있을 때 예외 던질지 여부
     * @param idPropName         ID 프로퍼티 명
     * @param naturalIdPropNames 자연키 프로퍼티 명들
     */
    default T merge(T entity,
            boolean existsThrow,
            String idPropName,
            String... naturalIdPropNames) {
        return mergeAllWithPolicy(List.of(entity), existsThrow, idPropName, null, naturalIdPropNames).get(0);
    }

    /**
     * 엔티티를 natural-id 로 매칭해 병합 후 저장. (정책 지정)
     */
    default T mergeWithPolicy(T entity,
            boolean existsThrow,
            String idPropName,
            String policyName,
            String... naturalIdPropNames) {
        return mergeAllWithPolicy(List.of(entity), existsThrow, idPropName, policyName, naturalIdPropNames).get(0);
    }

    /**
     * 엔티티를 ID 로 매칭해 병합 후 저장.
     *
     * @param entity      저장·병합할 엔티티
     * @param existsThrow 이미 있을 때 예외 던질지 여부
     * @param idPropName  ID 프로퍼티 명
     */
    default T mergeById(T entity,
            boolean existsThrow,
            String idPropName) {
        return mergeAllByIdWithPolicy(List.of(entity), existsThrow, idPropName, null).get(0);
    }

    /**
     * 엔티티를 ID 로 매칭해 병합 후 저장. (정책 지정)
     */
    default T mergeByIdWithPolicy(T entity,
            boolean existsThrow,
            String idPropName,
            String policyName) {
        return mergeAllByIdWithPolicy(List.of(entity), existsThrow, idPropName, policyName).get(0);
    }
}

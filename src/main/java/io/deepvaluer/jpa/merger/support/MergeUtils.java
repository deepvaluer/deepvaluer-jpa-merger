package io.deepvaluer.jpa.merger.support;

import org.springframework.beans.BeanUtils;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.util.Arrays;

import io.deepvaluer.jpa.merger.annotation.MergeField;
import io.deepvaluer.jpa.merger.annotation.MergePolicy;
import io.deepvaluer.jpa.merger.exception.MergeErrorCode;
import io.deepvaluer.jpa.merger.exception.MergeException;

public class MergeUtils {

    public static <T> void mergeAll(T target, T source) {
        // MergeField 애노테이션이 붙은 필드만 우선 처리
        Class<?> clz = target.getClass();
        for (PropertyDescriptor pd : BeanUtils.getPropertyDescriptors(clz)) {
            try {
                if (pd.getWriteMethod() == null || pd.getReadMethod() == null)
                    continue;
                String name = pd.getName();
                Field field = clz.getDeclaredField(name);
                MergeField mf = field.getAnnotation(MergeField.class);
                Object val = pd.getReadMethod().invoke(source);
                if (mf != null) {
                    if (mf.exclude())
                        continue;
                    if (val == null) {
                        if (mf.overwriteNull()) {
                            pd.getWriteMethod().invoke(target, (Object) null);
                            continue;
                        }
                        if (mf.ignoreNull())
                            continue;
                    }
                    pd.getWriteMethod().invoke(target, val);
                    continue;
                } else {
                    if (val == null) {
                        continue;
                    }
                    pd.getWriteMethod().invoke(target, val);
                }
            } catch (NoSuchFieldException e) {
                // 해당 필드 없음
            } catch (Exception ignored) {
            }
        }
    }

    public static <T> void merge(T target, T source) {
        MergePolicy[] policies = target.getClass().getAnnotationsByType(MergePolicy.class);
        MergePolicy defaultPol = Arrays.stream(policies)
                .filter(MergePolicy::isDefault)
                .findFirst()
                .orElseThrow(() -> new MergeException(MergeErrorCode.POLICY_NOT_FOUND,
                        "No default MergePolicy on " + target.getClass().getSimpleName()));
        merge(target, source, defaultPol.name());
    }

    public static <T> void merge(T target, T source, String policyName) {
        Class<?> clz = target.getClass();
        MergePolicy ann = Arrays.stream(clz.getAnnotationsByType(MergePolicy.class))
                .filter(p -> p.name().equals(policyName))
                .findAny()
                .orElseThrow(() -> new MergeException(MergeErrorCode.POLICY_NOT_FOUND,
                        "Unknown MergePolicy: " + policyName));

        MergeOptions opts = new MergeOptions.Builder()
                .overwriteNull(ann.overwriteNull())
                .overwriteNullFor(ann.overwriteNullFor())
                .excludeFields(ann.excludeFields())
                .build();

        for (PropertyDescriptor pd : BeanUtils.getPropertyDescriptors(clz)) {
            if (pd.getWriteMethod() == null || pd.getReadMethod() == null)
                continue;
            try {

                String name = pd.getName();

                // 필드 레벨 애노테이션 우선 체크
                Field field = clz.getDeclaredField(name);
                MergeField mf = field.getAnnotation(MergeField.class);
                Object val = pd.getReadMethod().invoke(source);

                if (mf != null) {
                    if (mf.exclude())
                        continue; // 제외
                    if (val == null) {
                        if (mf.overwriteNull()) { // null 덮어쓰기
                            pd.getWriteMethod().invoke(target, (Object) null);
                            continue;
                        }
                        if (mf.ignoreNull())
                            continue; // null 무시
                    }
                    // 값이 존재하면 무조건 복사
                    pd.getWriteMethod().invoke(target, val);
                    continue; // 정책 less
                }

                if (opts.getExcludeFields().contains(name))
                    continue;
                if (val == null && !opts.isOverwriteNull() && !opts.getOverwriteNullFields().contains(name))
                    if (!opts.isOverwriteForced())
                        continue;

                pd.getWriteMethod().invoke(target, val);
            } catch (Exception e) {
                throw new MergeException(MergeErrorCode.ACCESS_ERROR, "Error occurred while reading/writing properties", e);
            }
        }
    }

}

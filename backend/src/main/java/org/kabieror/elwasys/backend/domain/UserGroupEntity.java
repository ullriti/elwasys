package org.kabieror.elwasys.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entspricht der Tabelle {@code user_groups} (siehe docs/kb/02-data-model.md) sowie
 * {@code org.kabieror.elwasys.common.UserGroup} im Alt-Code.
 */
@Entity
@Table(name = "user_groups")
public class UserGroupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 50)
    private String name;

    /**
     * Postgres-natives Enum {@code DISCOUNT_TYPE}. {@code SqlTypes.NAMED_ENUM} lässt
     * Hibernate den Wert unter dem Enum-Typnamen binden (kein Cast-Fehler wie bei einer
     * einfachen VARCHAR-Bindung gegen eine Postgres-ENUM-Spalte).
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "discount_type", columnDefinition = "DISCOUNT_TYPE")
    private DiscountType discountType;

    @Column(name = "discount_value")
    private double discountValue;

    protected UserGroupEntity() {
        // for JPA
    }

    public UserGroupEntity(String name, DiscountType discountType, double discountValue) {
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
    }

    public Integer getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DiscountType getDiscountType() {
        return this.discountType;
    }

    public void setDiscountType(DiscountType discountType) {
        this.discountType = discountType;
    }

    public double getDiscountValue() {
        return this.discountValue;
    }

    public void setDiscountValue(double discountValue) {
        this.discountValue = discountValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserGroupEntity that)) {
            return false;
        }
        return this.id != null && this.id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

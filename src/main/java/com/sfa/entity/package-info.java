/**
 * Filter definitions are global in Hibernate — declared once here rather than
 * repeated per entity — and bound per-request by TenantFilterInterceptor.
 * {@code tenantIds} is a list: a normal session binds just its one active channel,
 * while a customer-portal session binds every channel the customer can order from.
 */
@org.hibernate.annotations.FilterDef(
        name = "tenantFilter",
        parameters = @org.hibernate.annotations.ParamDef(name = "tenantIds", type = java.util.UUID.class)
)
package com.sfa.entity;

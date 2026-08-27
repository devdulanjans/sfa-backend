/**
 * Filter definitions are global in Hibernate — declared once here rather than
 * repeated per entity — and bound per-request by TenantFilterInterceptor.
 */
@org.hibernate.annotations.FilterDef(
        name = "tenantFilter",
        parameters = @org.hibernate.annotations.ParamDef(name = "tenantId", type = java.util.UUID.class)
)
package com.sfa.entity;

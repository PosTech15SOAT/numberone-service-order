package br.com.fiap.numberone.shared.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Objects;
import java.util.UUID;

/** Single-branch deployment scope, assigned by trusted server configuration. */
@ConfigurationProperties("app.branch")
public record BranchSettings(UUID id) implements br.com.fiap.numberone.shared.application.gateways.BranchScope {
    public BranchSettings { Objects.requireNonNull(id, "OS_BRANCH_ID must be configured"); }
}

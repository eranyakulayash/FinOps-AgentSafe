package com.finops.agentsafe.repository;

import com.finops.agentsafe.domain.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {
}

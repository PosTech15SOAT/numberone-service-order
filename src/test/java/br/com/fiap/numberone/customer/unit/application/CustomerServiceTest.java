package br.com.fiap.numberone.customer.unit.application;

import br.com.fiap.numberone.customer.application.gateways.CustomerGateway;
import br.com.fiap.numberone.customer.application.services.CustomerService;
import br.com.fiap.numberone.customer.domain.entities.Customer;
import br.com.fiap.numberone.customer.domain.enums.TipoDocumento;
import br.com.fiap.numberone.customer.domain.exceptions.CustomerDocumentException;
import br.com.fiap.numberone.customer.domain.exceptions.CustomerNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerGateway customerGateway;

    private CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerService(customerGateway);
    }

    @Test
    void shouldCreateCustomerWhenDocumentIsValid() {
        // Arrange
        Customer newCustomer = customer(null, "Maria", "52998224725");
        Customer savedCustomer = customer(UUID.randomUUID(), "Maria", "52998224725");
        when(customerGateway.save(any(Customer.class))).thenReturn(savedCustomer);

        // Act
        Customer result = service.create(newCustomer);

        // Assert
        assertThat(result).isSameAs(savedCustomer);
        verify(customerGateway).save(newCustomer);
    }

    @Test
    void shouldThrowWhenCreatingCustomerWithInvalidDocument() {
        // Arrange
        Customer invalidCustomer = customer(null, "Maria", "123");

        // Act & Assert
        assertThatThrownBy(() -> service.create(invalidCustomer))
                .isInstanceOf(CustomerDocumentException.class)
                .hasMessage("CPF invalido");
        verify(customerGateway, never()).save(any());
    }

    @Test
    void shouldUpdateExistingCustomer() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        Customer currentCustomer = customer(customerId, "Maria", "52998224725");
        Customer newData = customer(null, "Maria Clara", "52998224725");
        Customer savedCustomer = customer(customerId, "Maria Clara", "52998224725");

        when(customerGateway.findById(customerId)).thenReturn(Optional.of(currentCustomer));
        when(customerGateway.save(any(Customer.class))).thenReturn(savedCustomer);

        // Act
        Customer result = service.update(customerId, newData);

        // Assert
        assertThat(result).isSameAs(savedCustomer);
        verify(customerGateway).findById(customerId);
        verify(customerGateway).save(any(Customer.class));
    }

    @Test
    void shouldThrowWhenUpdatingUnknownCustomer() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        Customer newData = customer(null, "Maria Clara", "52998224725");
        when(customerGateway.findById(customerId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.update(customerId, newData))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessage("Cliente nao encontrado para o id: " + customerId);
        verify(customerGateway, never()).save(any());
    }

    @Test
    void shouldReturnCustomerById() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        Customer existingCustomer = customer(customerId, "Maria", "52998224725");
        when(customerGateway.findById(customerId)).thenReturn(Optional.of(existingCustomer));

        // Act
        Customer result = service.findById(customerId);

        // Assert
        assertThat(result).isSameAs(existingCustomer);
    }

    @Test
    void shouldReturnAllCustomers() {
        // Arrange
        Customer firstCustomer = customer(UUID.randomUUID(), "Maria", "52998224725");
        Customer secondCustomer = customer(UUID.randomUUID(), "Joao", "11144477735");
        when(customerGateway.findAll()).thenReturn(List.of(firstCustomer, secondCustomer));

        // Act
        List<Customer> result = service.findAll();

        // Assert
        assertThat(result).containsExactly(firstCustomer, secondCustomer);
    }

    @Test
    void shouldDeleteExistingCustomer() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        Customer existingCustomer = customer(customerId, "Maria", "52998224725");
        when(customerGateway.findById(customerId)).thenReturn(Optional.of(existingCustomer));

        // Act
        service.delete(customerId);

        // Assert
        verify(customerGateway).delete(existingCustomer);
    }

    private static Customer customer(UUID id, String name, String document) {
        return Customer.builder()
                .id(id)
                .name(name)
                .documentType(TipoDocumento.PESSOA_FISICA)
                .document(document)
                .email("maria@example.com")
                .phone("11999999999")
                .address("Rua A")
                .active(true)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusHours(1))
                .build();
    }
}

// Complete Spring Integration 5.5 + Java 8 Compatible Unit Test Examples

// 1. Basic Message Channel Testing - Java 8 + Spring Integration 5.5 Compatible
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.annotation.Transformer;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.test.context.SpringIntegrationTest;
import org.springframework.integration.transformer.GenericTransformer;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.PollableChannel;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest
@SpringIntegrationTest
@ContextConfiguration(classes = TestConfig.class)
public class MessageChannelTest {
    
    @Autowired
    private MessageChannel inputChannel;
    
    @Autowired
    @Qualifier("outputChannel")
    private PollableChannel outputChannel;
    
    @Test
    public void testMessageFlow() {
        // Send message
        Message<String> message = MessageBuilder.withPayload("test payload").build();
        inputChannel.send(message);
        
        // Receive and verify
        Message<?> result = outputChannel.receive(1000);
        assertThat(result).isNotNull();
        assertThat(result.getPayload()).isEqualTo("test payload");
    }
}

// 2. Testing Service Activators - Java 8 Compatible
@RunWith(SpringRunner.class)
@SpringBootTest
@SpringIntegrationTest
@ContextConfiguration(classes = ServiceActivatorTestConfig.class)
public class ServiceActivatorTest {
    
    @Autowired
    private MessagingTemplate messagingTemplate;
    
    @MockBean
    private OrderService orderService;
    
    @Autowired
    private MessageChannel orderInputChannel;
    
    @Autowired
    @Qualifier("orderOutputChannel")
    private PollableChannel orderOutputChannel;
    
    @Test
    public void testOrderProcessing() {
        Order order = new Order("123", "Product A");
        ProcessedOrder expectedResult = new ProcessedOrder("123", "PROCESSED");
        
        when(orderService.processOrder(any(Order.class))).thenReturn(expectedResult);
        
        // Send order for processing
        messagingTemplate.convertAndSend("orderInputChannel", order);
        
        // Receive processed result
        Message<?> result = orderOutputChannel.receive(1000);
        assertThat(result).isNotNull();
        assertThat(result.getPayload()).isInstanceOf(ProcessedOrder.class);
        
        ProcessedOrder processedOrder = (ProcessedOrder) result.getPayload();
        assertThat(processedOrder.getStatus()).isEqualTo("PROCESSED");
        verify(orderService).processOrder(order);
    }
}

// 3. Testing Message Transformers - Java 8 Compatible
@RunWith(SpringRunner.class)
@SpringBootTest
@SpringIntegrationTest
@ContextConfiguration(classes = TransformerTestConfig.class)
public class TransformerTest {
    
    @Autowired
    @Qualifier("transformerInputChannel")
    private MessageChannel transformerInputChannel;
    
    @Autowired
    @Qualifier("transformerOutputChannel")  
    private PollableChannel transformerOutputChannel;
    
    @Test
    public void testStringToUpperCaseTransformation() {
        Message<String> input = MessageBuilder.withPayload("hello world").build();
        transformerInputChannel.send(input);
        
        Message<?> output = transformerOutputChannel.receive(1000);
        assertThat(output).isNotNull();
        assertThat(output.getPayload()).isEqualTo("HELLO WORLD");
    }
    
    @Test
    public void testStringToLowerCaseTransformation() {
        Message<String> input = MessageBuilder.withPayload("HELLO WORLD").build();
        transformerInputChannel.send(input);
        
        Message<?> output = transformerOutputChannel.receive(1000);
        assertThat(output).isNotNull();
        assertThat(output.getPayload()).isEqualTo("hello world");
    }
}

// 4. Testing Error Handling - Java 8 Compatible (Updated from previous example)
@RunWith(SpringRunner.class)
@SpringBootTest
@SpringIntegrationTest
@ContextConfiguration(classes = ErrorHandlingTestConfig.class)
public class ErrorHandlingTest {
    
    @Autowired
    private MessageChannel errorProneChannel;
    
    @Autowired
    @Qualifier("errorChannel")
    private PollableChannel errorChannel;
    
    @Test
    public void testErrorHandling() {
        Message<String> invalidMessage = MessageBuilder
            .withPayload("invalid-data")
            .setHeader("simulate-error", true)
            .build();
        
        errorProneChannel.send(invalidMessage);
        
        Message<?> errorMessage = errorChannel.receive(1000);
        assertThat(errorMessage).isNotNull();
        assertThat(errorMessage.getPayload()).isInstanceOf(MessagingException.class);
    }
    
    @Test
    public void testSpecificErrorTypes() {
        // Test different error scenarios
        Message<String> nullPointerMessage = MessageBuilder
            .withPayload("null-pointer")
            .build();
        
        errorProneChannel.send(nullPointerMessage);
        
        Message<?> errorMessage = errorChannel.receive(1000);
        MessagingException exception = (MessagingException) errorMessage.getPayload();
        assertThat(exception.getCause()).isInstanceOf(NullPointerException.class);
    }
}

// 5. Testing Gateway Interfaces - Java 8 Compatible
@RunWith(SpringRunner.class)
@SpringBootTest
@SpringIntegrationTest
@ContextConfiguration(classes = GatewayTestConfig.class)
public class GatewayTest {
    
    @Autowired
    private OrderGateway orderGateway;
    
    @MockBean
    private OrderRepository orderRepository;
    
    @Test
    public void testGatewayMethod() {
        Order order = new Order("456", "Product B");
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        
        String result = orderGateway.processOrder("Product B", 2);
        
        assertThat(result).contains("Order processed");
        verify(orderRepository).save(any(Order.class));
    }
    
    @Test
    public void testGatewayWithHeaders() {
        String result = orderGateway.processOrderWithPriority("Product C", 1, "HIGH");
        
        assertThat(result).contains("HIGH priority");
        assertThat(result).contains("Product C");
    }
}

// 6. Testing with Message History - Java 8 Compatible
@RunWith(SpringRunner.class)
@SpringBootTest
@SpringIntegrationTest
@ContextConfiguration(classes = MessageHistoryTestConfig.class)
public class MessageHistoryTest {
    
    @Autowired
    private MessageChannel trackedInputChannel;
    
    @Autowired
    @Qualifier("trackedOutputChannel")
    private PollableChannel trackedOutputChannel;
    
    @Test
    public void testMessageHistory() {
        Message<String> message = MessageBuilder.withPayload("tracked message").build();
        trackedInputChannel.send(message);
        
        Message<?> result = trackedOutputChannel.receive(1000);
        assertThat(result).isNotNull();
        
        // Note: Message history testing requires additional configuration in Spring Integration 5.5
        // The history is available but requires explicit enablement
    }
}

// ========== CONFIGURATION CLASSES ==========

// Basic Test Configuration
@Configuration
@EnableIntegration
public class TestConfig {
    
    @Bean
    public MessageChannel inputChannel() {
        return new DirectChannel();
    }
    
    @Bean(name = "outputChannel")
    public PollableChannel outputChannel() {
        return new QueueChannel();
    }
    
    @Bean
    @ServiceActivator(inputChannel = "inputChannel", outputChannel = "outputChannel")
    public MessageHandler echoHandler() {
        return new MessageHandler() {
            @Override
            public void handleMessage(Message<?> message) throws MessagingException {
                // Simple echo handler for testing
                outputChannel().send(message);
            }
        };
    }
    
    @Bean
    public MessagingTemplate messagingTemplate() {
        return new MessagingTemplate();
    }
}

// Service Activator Test Configuration
@Configuration
@EnableIntegration
public class ServiceActivatorTestConfig {
    
    @Bean
    public MessageChannel orderInputChannel() {
        return new DirectChannel();
    }
    
    @Bean(name = "orderOutputChannel")
    public PollableChannel orderOutputChannel() {
        return new QueueChannel();
    }
    
    @Bean
    @ServiceActivator(inputChannel = "orderInputChannel", outputChannel = "orderOutputChannel")
    public MessageHandler orderProcessingHandler(OrderService orderService) {
        return new MessageHandler() {
            @Override
            public void handleMessage(Message<?> message) throws MessagingException {
                Order order = (Order) message.getPayload();
                ProcessedOrder result = orderService.processOrder(order);
                
                Message<ProcessedOrder> responseMessage = MessageBuilder
                    .withPayload(result)
                    .copyHeaders(message.getHeaders())
                    .build();
                
                orderOutputChannel().send(responseMessage);
            }
        };
    }
    
    @Bean
    public MessagingTemplate messagingTemplate() {
        return new MessagingTemplate();
    }
}

// Transformer Test Configuration
@Configuration
@EnableIntegration
public class TransformerTestConfig {
    
    @Bean
    public MessageChannel transformerInputChannel() {
        return new DirectChannel();
    }
    
    @Bean(name = "transformerOutputChannel")
    public PollableChannel transformerOutputChannel() {
        return new QueueChannel();
    }
    
    @Bean
    @Transformer(inputChannel = "transformerInputChannel", outputChannel = "transformerOutputChannel")
    public GenericTransformer<String, String> caseTransformer() {
        return new GenericTransformer<String, String>() {
            @Override
            public String transform(String source) {
                // Simple transformation logic for testing
                if (source.equals(source.toUpperCase())) {
                    return source.toLowerCase();
                } else {
                    return source.toUpperCase();
                }
            }
        };
    }
}

// Error Handling Test Configuration
@Configuration
@EnableIntegration
public class ErrorHandlingTestConfig {
    
    @Bean(name = "errorChannel")
    public PollableChannel errorChannel() {
        return new QueueChannel();
    }
    
    @Bean
    public MessageChannel errorProneChannel() {
        return new DirectChannel();
    }
    
    @Bean
    @ServiceActivator(inputChannel = "errorProneChannel")
    public MessageHandler errorThrowingHandler() {
        return new MessageHandler() {
            @Override
            public void handleMessage(Message<?> message) throws MessagingException {
                String payload = (String) message.getPayload();
                
                if ("invalid-data".equals(payload)) {
                    throw new RuntimeException("Intentional test error");
                }
                
                if ("null-pointer".equals(payload)) {
                    throw new NullPointerException("Null pointer test error");
                }
                
                if (message.getHeaders().containsKey("simulate-error")) {
                    throw new IllegalArgumentException("Header-triggered error");
                }
                
                // Normal processing
                System.out.println("Processed: " + payload);
            }
        };
    }
}

// Gateway Test Configuration
@Configuration
@EnableIntegration
public class GatewayTestConfig {
    
    @Bean
    public MessageChannel orderProcessingChannel() {
        return new DirectChannel();
    }
    
    @Bean
    public MessageChannel priorityOrderChannel() {
        return new DirectChannel();
    }
    
    @Bean
    @ServiceActivator(inputChannel = "orderProcessingChannel")
    public MessageHandler orderGatewayHandler(OrderRepository orderRepository) {
        return new MessageHandler() {
            @Override
            public void handleMessage(Message<?> message) throws MessagingException {
                String product = (String) message.getHeaders().get("product");
                Integer quantity = (Integer) message.getHeaders().get("quantity");
                
                Order order = new Order(java.util.UUID.randomUUID().toString(), product);
                orderRepository.save(order);
                
                // Return response (this would be handled by gateway reply mechanism)
            }
        };
    }
    
    @Bean
    @ServiceActivator(inputChannel = "priorityOrderChannel")
    public MessageHandler priorityOrderHandler() {
        return new MessageHandler() {
            @Override
            public void handleMessage(Message<?> message) throws MessagingException {
                // Priority order processing logic
                System.out.println("Processing priority order: " + message.getPayload());
            }
        };
    }
}

// Message History Test Configuration
@Configuration
@EnableIntegration
// @EnableMessageHistory // Uncomment if you want to enable message history tracking
public class MessageHistoryTestConfig {
    
    @Bean
    public MessageChannel trackedInputChannel() {
        return new DirectChannel();
    }
    
    @Bean(name = "trackedOutputChannel")
    public PollableChannel trackedOutputChannel() {
        return new QueueChannel();
    }
    
    @Bean
    @ServiceActivator(inputChannel = "trackedInputChannel", outputChannel = "trackedOutputChannel")
    public MessageHandler trackingHandler() {
        return new MessageHandler() {
            @Override
            public void handleMessage(Message<?> message) throws MessagingException {
                // Simple pass-through handler that maintains message history
                trackedOutputChannel().send(message);
            }
        };
    }
}

// ========== DOMAIN OBJECTS ==========

// Sample Gateway Interface - Java 8 Compatible
@MessagingGateway
public interface OrderGateway {
    
    @Gateway(requestChannel = "orderProcessingChannel",
             headers = @GatewayHeader(name = "product", expression = "#args[0]"),
             replyTimeout = 1000)
    String processOrder(String product, int quantity);
    
    @Gateway(requestChannel = "priorityOrderChannel",
             headers = {
                 @GatewayHeader(name = "product", expression = "#args[0]"),
                 @GatewayHeader(name = "priority", expression = "#args[2]")
             })
    String processOrderWithPriority(String product, int quantity, String priority);
}

// Sample Domain Objects
public class Order {
    private String id;
    private String product;
    
    public Order() {} // Default constructor for Spring
    
    public Order(String id, String product) {
        this.id = id;
        this.product = product;
    }
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Order order = (Order) obj;
        return java.util.Objects.equals(id, order.id) && 
               java.util.Objects.equals(product, order.product);
    }
    
    @Override
    public int hashCode() {
        return java.util.Objects.hash(id, product);
    }
}

public class ProcessedOrder {
    private String id;
    private String status;
    
    public ProcessedOrder() {} // Default constructor
    
    public ProcessedOrder(String id, String status) {
        this.id = id;
        this.status = status;
    }
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

// Sample Service Interface
public interface OrderService {
    ProcessedOrder processOrder(Order order);
}

// Sample Repository Interface  
public interface OrderRepository {
    Order save(Order order);
    Order findById(String id);
}

/*
Required dependencies for pom.xml (Spring Integration 5.5 + Java 8):

<properties>
    <java.version>8</java.version>
    <maven.compiler.source>8</maven.compiler.source>
    <maven.compiler.target>8</maven.compiler.target>
    <spring-boot.version>2.7.18</spring-boot.version>
    <spring-integration.version>5.5.20</spring-integration.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-integration</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.integration</groupId>
        <artifactId>spring-integration-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>junit</groupId>
        <artifactId>junit</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-core</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
*/
1. Applying a Factory + Strategy pattern combination
- Strategy Pattern → different converters implement a common interface
- Factory Pattern → creates and manages converter instances based on type (with caching)

```mermaid
classDiagram

%% Interface (Strategy)
class ConverterStrategy {
    <<interface>>
    +convert(value : String) Object
}

%% Concrete Strategies (pseudo names)
class TextStrategy
class NumericStrategy
class DateStrategy
class TimeStrategy
class TimestampStrategy

ConverterStrategy <|.. TextStrategy
ConverterStrategy <|.. NumericStrategy
ConverterStrategy <|.. DateStrategy
ConverterStrategy <|.. TimeStrategy
ConverterStrategy <|.. TimestampStrategy

%% Enum (type selector)
class ValueType {
    <<enum>>
    TEXT
    OBJECT
    NUMBER
    DATE
    TIME
    TIMESTAMP
}

%% Factory (with cache)
class ConverterFactory {
    -cache : Map<ValueType, ConverterStrategy>
    +getConverter(type : ValueType) ConverterStrategy
    -create(type : ValueType) ConverterStrategy
    note for create "Use switch-case on ValueType to instantiate correct strategy"
    +validate(type : ValueType, value : String)
}

%% Relationships
ConverterStrategy --> ConverterFactory : contains
ConverterFactory --> ValueType : uses
```
=> used in converter

2. Applying `Chain of responsibility` combined with `Factory` and `Builder Pattern`
- Chain of responsibility Pattern → organizes multiple validators into a chain (like a linked list), where each validator processes the request or passes it to the next one
- Factory Pattern → responsible for creating and providing preconfigured validator chains (and optionally caching/reusing them).
- Builder Pattern -> used to fluently construct the validator chain step-by-step (linking handlers together in order).

```mermaid
classDiagram

%% Abstract Handler (Chain of Responsibility)
class ValidationHandler {
    <<abstract>>
    -next : ValidationHandler
    +setNext(handler : ValidationHandler)
    +validate(config, value) String
    #hasScope(config) boolean
    #hasError(config, value) boolean
    #getErrorMessage(config) String
}

%% Concrete Handlers (pseudo names)
class RequiredRule
class TypeRule
class LengthRule
class PatternRule
class ListRule
class RangeRule

ValidationHandler <|-- RequiredRule
ValidationHandler <|-- TypeRule
ValidationHandler <|-- LengthRule
ValidationHandler <|-- PatternRule
ValidationHandler <|-- ListRule
ValidationHandler <|-- RangeRule

%% Builder (to construct chain)
class ChainBuilder {
    -head : ValidationHandler
    +on(handler : ValidationHandler) ChainBuilder
    +build() ValidationHandler
}

%% Factory (predefined chains)
class ValidatorFactory {
    -defaultChain : ValidationHandler
    -optionalChain : ValidationHandler
    +getDefault() ValidationHandler
    note for getDefault "Uses Builder.on(...).on(...).build() to create a linked chain of handlers"
    +getOptional() ValidationHandler
}

%% Relationships
ValidationHandler --> ValidationHandler : next
ValidatorFactory --> ChainBuilder : uses
ValidatorFactory --> ValidationHandler : provides
```

=> used in validator
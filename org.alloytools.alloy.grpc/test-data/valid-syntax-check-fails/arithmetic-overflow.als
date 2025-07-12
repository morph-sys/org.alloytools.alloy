// Model with valid syntax but arithmetic constraints that cause overflow/underflow
module test/arithmetic_overflow

sig Counter {
    value: Int
}

fact ArithmeticConstraints {
    // All counters must have maximum integer value
    all c: Counter | c.value = 2147483647  // Max 32-bit signed int
    
    // But also must be one more than maximum (overflow)
    all c: Counter | c.value = add[2147483647, 1]
    
    // Must have exactly one counter
    one Counter
}

fact OverflowScenarios {
    some c: Counter | {
        // Value must be both maximum and minimum simultaneously
        c.value = 2147483647
        c.value = -2147483648
        
        // Multiplication overflow
        c.value = mul[1000000, 1000000]  // Would overflow in 32-bit
        
        // Division by zero scenario (if supported)
        c.value = div[100, 0]
    }
}

sig Account {
    balance: Int,
    transactions: set Transaction
}

sig Transaction {
    amount: Int,
    account: one Account
}

fact BankingConstraints {
    // All balances must be positive
    all a: Account | a.balance > 0
    
    // Sum of all transaction amounts must equal balance
    all a: Account | a.balance = sum t: a.transactions | t.amount
    
    // But each transaction amount must be larger than any possible balance
    all t: Transaction | t.amount > 2147483647
    
    // And we must have at least one transaction
    all a: Account | some a.transactions
}

fact IntegerBounds {
    // Create impossible integer constraints
    some x: Int | {
        x > 2147483647  // Greater than max int
        x < -2147483648 // Less than min int
        x = add[x, 1]   // Self-referential arithmetic
    }
}

pred ValidAccounting {
    some Account
    all a: Account | a.balance >= 0
}

// These will fail due to arithmetic impossibilities
run ValidAccounting for 3
run ValidAccounting for 3 but 4 int
run ValidAccounting for 5 but 8 int

// Model with intentional syntax errors for testing error handling
module test/syntax_error

sig Person {
    name: String,
    friends: set Person
    // Missing comma above - syntax error

fact PersonFacts {
    // Missing closing brace - syntax error
    all p: Person | p not in p.friends

pred SomePeople {
    some Person
    // Missing closing brace - syntax error

// Missing run command closing
run SomePeople for 3

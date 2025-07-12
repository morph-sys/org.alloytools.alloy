// Simple person model for testing basic functionality
module test/simple_person

sig Person {
    friends: set Person,
    age: Int
}

fact NoSelfFriendship {
    no p: Person | p in p.friends
}

fact SymmetricFriendship {
    all p1, p2: Person | p1 in p2.friends implies p2 in p1.friends
}

fact ReasonableAge {
    all p: Person | p.age >= 0 and p.age <= 150
}

pred SomeFriendships {
    some p: Person | some p.friends
}

pred AllHaveFriends {
    all p: Person | some p.friends
}

fun OldestPerson: Person {
    {p: Person | all p2: Person | p.age >= p2.age}
}

assert FriendshipIsSymmetric {
    all p1, p2: Person | p1 in p2.friends iff p2 in p1.friends
}

// Test commands
run SomeFriendships for 3
run AllHaveFriends for 4
check FriendshipIsSymmetric for 5
run {} for 2 but exactly 2 Person

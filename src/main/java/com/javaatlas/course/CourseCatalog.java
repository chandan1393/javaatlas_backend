package com.javaatlas.course;

import java.util.List;

/**
 * Ready-made paid courses, offered in Admin -> Course templates. Adding one creates an UNPUBLISHED draft with the
 * full syllabus and an outline for every lecture. The courses are text-based: expand each outline into the full
 * lesson text (lectures can embed interactive labs with a line like "::lab threads:race"), then publish.
 *
 * Design rules these courses follow, so buyers don't regret paying:
 * - Each one gives something the free lessons can't: a complete project, guided practice or interview drills.
 * - The course page says who it's for AND who it's not for, the prerequisites and exactly what you'll build.
 * - Several lectures are free previews, and the whole syllabus is visible before buying.
 * - Honest single prices, no fake "95% off" countdowns.
 */
final class CourseCatalog {

    private CourseCatalog() {
    }

    /** Fresh instances on every call (they're saved as new rows). */
    static List<Course> all() {
        return List.of(springBootInPractice(), microservicesInPractice(), interviewPrep(), dsaInJava(), lowLevelDesign());
    }

    private static Course course(String slug, String title, String subtitle, String description, String level,
                                 int priceInr, int order, String... outcomes) {
        Course c = Course.create(slug);
        c.update(slug, title, subtitle, description, List.of(outcomes), level, priceInr, false, order);
        return c;
    }

    private static Lecture l(String title, int minutes, String... points) {
        return Lecture.of(title, minutes, false, notes(points));
    }

    private static Lecture free(String title, int minutes, String... points) {
        return Lecture.of(title, minutes, true, notes(points));
    }

    /** A lecture whose text ends with an interactive lab ("::lab kind:preset", rendered by the course player). */
    private static Lecture lab(String title, int minutes, String labSpec, String... points) {
        return Lecture.of(title, minutes, false, notes(points) + "\n\n::lab " + labSpec);
    }

    private static String notes(String... points) {
        StringBuilder sb = new StringBuilder("## In this lecture\n");
        for (String p : points) {
            sb.append("- ").append(p).append('\n');
        }
        return sb.toString().trim();
    }

    // ------------------------------------------------------------------------------------------------
    private static Course springBootInPractice() {
        return course("spring-boot-in-practice", "Spring Boot in Practice: Build and Deploy a Production REST API",
                "Build EventHub, a real event-booking API with PostgreSQL, JWT security, tests, Docker and CI, and deploy it. One portfolio project you can explain in any interview.",
                """
                ## What you'll build
                **EventHub**, an event-booking backend: organisers publish events, attendees book seats, payments are confirmed, and nobody can ever buy the last seat twice. You write every line, and the finished project lives on your GitHub with a live URL you can put on your resume.

                ## Who this course is for
                - You know core Java and have seen Spring Boot basics (the free Spring lessons on this site are enough).
                - You want one serious, deployable project instead of another to-do app.
                - You're preparing for backend interviews and want a strong answer to "tell me about your project".

                ## Not for you if
                - You're new to Java. Start with the free lessons: they're complete, and they're free.
                - You want microservices first. Take this course, then Microservices in Practice.

                ## Prerequisites
                - Core Java (classes, collections, lambdas), basic SQL and Git.
                - Java 21 or newer, an IDE (IntelliJ IDEA Community is fine) and Docker Desktop.

                ## Why it's worth it
                - Production habits, not toy code: validation, one error format, migrations, transactions, security, tests, CI and deployment.
                - Every lecture ends at a runnable step, so you can't get lost.
                - The hard parts other courses skip: double booking, refresh tokens, Testcontainers and idempotent payments.
                """,
                "Intermediate", 1999, 10,
                "Design and build a REST API that follows real-world conventions",
                "Model data with JPA and PostgreSQL, manage the schema with Flyway and avoid N+1 queries",
                "Prevent double booking with transactions and optimistic or pessimistic locking",
                "Secure the API with Spring Security, JWT access and refresh tokens, and roles",
                "Test with JUnit 5, Mockito, MockMvc and Testcontainers",
                "Ship with Docker, GitHub Actions and a cloud deployment on your own domain",
                "Explain every design decision confidently in an interview")
            .addSection("Welcome and the project",
                free("What we'll build and how to get the most from this course", 6,
                    "The EventHub features and the architecture at a glance",
                    "How each section builds on the last, and how to use the lecture notes",
                    "How to ask questions and where the finished code lives"),
                free("A tour of the finished EventHub API", 8,
                    "Booking a seat end to end: sign up, browse, hold, pay, confirm",
                    "What happens when two people try to book the last seat at once"),
                free("Setting up Java, IntelliJ, Docker and PostgreSQL", 10,
                    "Installing a current JDK and checking the setup",
                    "Running PostgreSQL in Docker with one command"))
            .addSection("REST API foundations",
                l("Project setup and a clean package structure", 10, "Spring Initializr choices and why", "Packaging by feature instead of by layer"),
                l("Designing resources and URLs for EventHub", 12, "Nouns, verbs and status codes", "Designing the booking flow as resources"),
                l("Controllers, DTOs and records", 14, "Why entities never leave the service layer", "Mapping with records and small mapper methods"),
                l("Validation with Bean Validation and custom validators", 12, "@Valid, constraint annotations and groups", "A custom validator for event dates"),
                free("One error format for the whole API with ProblemDetail", 12, "@RestControllerAdvice and RFC 9457 ProblemDetail", "Field errors clients can show next to form inputs"),
                l("Pagination, sorting and filtering", 12, "Pageable and safe sort parameters", "Filtering events by city, date and price"))
            .addSection("Data with JPA and PostgreSQL",
                l("Entities for events, venues, seats and bookings", 15, "Modelling the domain", "Choosing identifiers and value types"),
                l("Schema migrations with Flyway", 10, "Why ddl-auto is not for production", "Writing and versioning migrations"),
                l("Relationships done right: ownership and fetch types", 14, "Owning side, mappedBy and cascades", "Why everything should be LAZY"),
                free("Finding and fixing N+1 queries", 14, "Spotting N+1 in the SQL log", "Fixing it with fetch joins, entity graphs and batch fetching"),
                l("Projections and custom queries", 12, "Interface and record projections", "When to drop to native SQL"))
            .addSection("Bookings without double-selling",
                l("What @Transactional really does", 14, "Proxies, propagation and rollback rules", "The self-invocation trap"),
                lab("The race: two users, one seat", 10, "threads:race", "Reproducing a double booking with a concurrent test"),
                l("Optimistic locking with @Version", 12, "How version checks work", "Retrying safely"),
                l("Pessimistic locking and when to use it", 12, "SELECT ... FOR UPDATE with JPA", "Timeouts and deadlock risks"),
                l("Seat holds that expire", 12, "Holding seats during checkout", "Cleaning up expired holds with a scheduled job"),
                l("Idempotent payment confirmation", 12, "Why payment callbacks arrive twice", "Idempotency keys and unique constraints"))
            .addSection("Security",
                l("Spring Security's filter chain in 10 minutes", 10, "What happens to every request", "SecurityFilterChain configuration"),
                l("Sign-up and login with BCrypt", 12, "Storing passwords safely", "Login endpoints and error messages that don't leak"),
                l("JWT access tokens", 14, "Signing and validating tokens", "Resource server configuration"),
                l("Refresh tokens and logout", 14, "Short-lived access, long-lived refresh", "Rotation and revocation"),
                l("Roles: organiser vs attendee", 12, "Method security with @PreAuthorize", "Checking ownership of resources"),
                l("CORS, rate limiting and security headers", 12, "CORS for a separate frontend", "A simple rate limiter for login"))
            .addSection("Testing",
                l("What to test and how", 8, "The testing pyramid for APIs"),
                l("Unit tests with JUnit 5 and Mockito", 14, "Testing the booking service", "Mocks vs fakes"),
                l("Web layer tests with MockMvc", 12, "Testing status codes, JSON and validation errors"),
                l("Integration tests with Testcontainers", 14, "A real PostgreSQL in tests", "Reusing containers for speed"),
                l("Testing security and the booking race", 12, "Tests with authenticated users", "A concurrency test that proves no double booking"))
            .addSection("Performance and operations",
                l("Caching event listings with Redis", 12, "@Cacheable and eviction", "What not to cache"),
                l("API documentation with OpenAPI", 8, "Generating docs and a try-it page"),
                l("Actuator, health checks and structured logs", 10, "Health probes for deployment", "Logs you can search"),
                l("Configuration and profiles for dev and prod", 10, "Properties, profiles and secrets from the environment"))
            .addSection("Ship it",
                l("A small, secure Docker image", 12, "Multi-stage builds and a non-root user"),
                l("docker compose for the whole stack", 10, "API, PostgreSQL and Redis locally"),
                l("CI with GitHub Actions", 12, "Build, test and publish an image on every push"),
                l("Deploying with a real domain and HTTPS", 14, "Deploying to Railway or any cloud", "Environment variables, domains and certificates"))
            .addSection("Interview-ready",
                free("Explaining EventHub in an interview", 8, "A two-minute project story that interviewers remember"),
                l("20 questions interviewers ask about this project, answered", 16, "Locking, transactions, security, testing and trade-offs"),
                l("Where to take the project next", 6, "Extensions that make it stand out"));
    }

    // ------------------------------------------------------------------------------------------------
    private static Course microservicesInPractice() {
        return course("microservices-in-practice", "Microservices in Practice: Spring Boot, Kafka, Docker and Kubernetes",
                "Build QuickBite, a food-delivery platform of five services, and learn the patterns that make microservices work in production: gateways, events, sagas, resilience and observability.",
                """
                ## What you'll build
                **QuickBite**, a food-delivery platform: order, restaurant, payment, delivery and notification services behind an API gateway, talking over REST and Kafka, running in Docker and on Kubernetes, with tracing across every call.

                ## Who this course is for
                - You're comfortable building a single Spring Boot API with JPA and security.
                - You work on (or are interviewing for) teams that run microservices.
                - You want to understand *why* each pattern exists, not just copy configuration.

                ## Not for you if
                - You haven't built a Spring Boot REST API yet. Take Spring Boot in Practice first.
                - You only need a quick overview. The free microservices lessons cover the concepts.

                ## Prerequisites
                - Spring Boot, Spring Data JPA and REST APIs.
                - Docker basics. No Kafka or Kubernetes experience needed.
                - A machine with 16 GB of RAM is recommended to run everything locally.

                ## Why it's worth it
                - One realistic system instead of disconnected demos.
                - We break things on purpose: failed payments, slow services, duplicate events, and fix them properly.
                - An honest lecture on when *not* to use microservices, which interviewers love.
                """,
                "Advanced", 2999, 20,
                "Split a system into services with clear boundaries and their own databases",
                "Route and secure traffic with an API gateway",
                "Use Kafka for events with retries, dead-letter topics and idempotent consumers",
                "Keep data consistent across services with the saga and outbox patterns",
                "Make services resilient with timeouts, retries, circuit breakers and bulkheads",
                "Trace requests across services and monitor them with Prometheus and Grafana",
                "Run the platform with Docker Compose and deploy it to Kubernetes")
            .addSection("Welcome",
                free("What we'll build", 6, "The QuickBite services and how they talk to each other"),
                free("When microservices are the wrong choice", 10, "The costs nobody mentions", "Modular monoliths as a real alternative"),
                free("The architecture of QuickBite", 10, "Synchronous vs asynchronous communication", "Where each pattern in this course fits"))
            .addSection("From monolith to services",
                l("Finding service boundaries with domain-driven design", 14, "Bounded contexts and aggregates", "Signs a boundary is wrong"),
                l("A database per service", 10, "Why shared databases hurt", "Handling data other services need"),
                l("Service-to-service calls with RestClient and HTTP interfaces", 12, "Declarative clients", "Error handling between services"),
                l("Configuration management", 10, "Externalised configuration and secrets"))
            .addSection("Gateway and discovery",
                l("An API gateway with Spring Cloud Gateway", 14, "Routes, filters and rate limits"),
                l("Service discovery: Eureka vs Kubernetes DNS", 12, "What each gives you and when to choose which"),
                l("Authentication at the edge with JWT", 14, "Validating tokens once and passing identity along"))
            .addSection("Events with Kafka",
                free("Kafka in 15 minutes", 15, "Topics, partitions, offsets and consumer groups", "Ordering and delivery guarantees"),
                l("Publishing order events", 12, "Event design and schemas"),
                l("Consumers, retries and dead-letter topics", 14, "Handling poison messages"),
                l("The transactional outbox pattern", 14, "Never lose an event when the database commits"),
                l("Idempotent consumers", 10, "Processing each event exactly once in effect"))
            .addSection("Distributed transactions",
                l("Why two-phase commit doesn't scale, and what sagas do instead", 12, "Choreography vs orchestration"),
                l("An orchestrated saga for placing an order", 16, "Order, payment and restaurant steps"),
                l("Compensations and failure scenarios", 14, "Payment fails, restaurant rejects, timeouts"))
            .addSection("Resilience",
                l("Timeouts, retries and circuit breakers with Resilience4j", 14, "Configuring each and testing it"),
                l("Bulkheads and rate limiters", 10, "Stopping one slow dependency taking everything down"),
                l("Graceful degradation", 8, "Fallbacks users can live with"))
            .addSection("Observability",
                l("Distributed tracing with Micrometer Tracing and Zipkin", 14, "Following one order across five services"),
                l("Centralised logs with correlation IDs", 10, "Finding everything about one request"),
                l("Metrics with Prometheus and Grafana", 14, "Dashboards and alerts that matter"))
            .addSection("Containers and Kubernetes",
                l("Docker images for every service", 10, "Consistent, small images"),
                l("docker compose for local development", 10, "Running the whole platform on your laptop"),
                l("Kubernetes essentials: pods, deployments and services", 16, "Just enough Kubernetes to deploy with confidence"),
                l("Deploying QuickBite to Kubernetes", 16, "Manifests, rolling updates and scaling"),
                l("Configuration, secrets and health probes", 12, "ConfigMaps, Secrets, liveness and readiness"))
            .addSection("Testing microservices",
                l("Contract tests with Spring Cloud Contract", 14, "Changing an API without breaking its consumers"),
                l("Integration tests with Testcontainers and Kafka", 14, "Testing event flows for real"),
                l("End-to-end smoke tests", 8, "A few tests that prove the system works"))
            .addSection("Interview-ready",
                l("Designing microservices in interviews: a framework", 12, "From requirements to services, data and communication"),
                l("25 microservices interview questions, answered", 18, "Consistency, resilience, observability and trade-offs"));
    }

    // ------------------------------------------------------------------------------------------------
    private static Course interviewPrep() {
        return course("java-backend-interview-prep", "Java Backend Interview Prep: 400 Real Questions, Explained",
                "Every topic Java backend interviews cover, from core Java and Spring to SQL, system design and coding rounds, with answers explained the way interviewers want to hear them, interactive labs, and full mock-interview transcripts.",
                """
                ## What you get
                Around 400 real interview questions grouped by topic, each with a short answer to say out loud, the deeper explanation behind it, and code where it helps. Plus the coding round, the low-level design round, resume and project advice, interactive labs, and mock interviews written out in full with feedback.

                ## Who this course is for
                - Java developers with 0 to 6 years of experience preparing for service, product or startup interviews.
                - You know the topics but freeze when asked to explain them clearly and quickly.
                - You want one structured plan instead of scattered videos and PDFs, with interactive labs instead of passive watching.

                ## Not for you if
                - You're still learning Java basics. Use the free lessons first, then come back.
                - You're preparing for senior architect or management roles. This course targets developer rounds.

                ## Prerequisites
                - Working knowledge of Java and some Spring Boot.

                ## Why it's worth it
                - Answers are structured the way interviewers score them: definition, example, trade-off.
                - Mock-interview transcripts show what good and weak answers actually sound like.
                - The resume and project lectures fix the reasons most applications never get a call.
                """,
                "Intermediate", 999, 30,
                "Answer core Java, collections and multithreading questions clearly and confidently",
                "Explain Spring Boot, JPA and transactions the way interviewers expect",
                "Solve SQL questions including joins, indexes and window functions",
                "Handle system design and low-level design rounds with a repeatable approach",
                "Crack common coding-round problems using a small set of patterns",
                "Present your resume and projects so they lead to good questions",
                "Negotiate your offer with confidence")
            .addSection("Start here",
                free("How Java backend interviews work in India", 10, "Service companies, product companies and startups", "The typical rounds and what each one tests"),
                free("Your preparation plan", 8, "A 4-week and an 8-week plan", "How to use the question lists"),
                free("A resume that gets shortlisted", 12, "Projects, impact and keywords", "A resume template you can copy"))
            .addSection("Core Java",
                lab("OOP questions with code", 16, "memory:upcasting", "Pillars, overloading vs overriding, abstract class vs interface"),
                lab("Strings, immutability and the string pool", 12, "memory:string-pool", "Why String is immutable and what that buys you"),
                Lecture.of("equals, hashCode and HashMap internals", 16, true, notes("The questions asked in almost every interview", "Answering at three depths: junior, mid and senior") + "\n\n::lab hashmap:collision"),
                l("Exceptions: design and tricky cases", 12, "Checked vs unchecked, finally, try-with-resources"),
                lab("Java 8 to 25 features they ask about", 16, "stream:sorted", "Streams, Optional, records, sealed types, pattern matching"))
            .addSection("Collections",
                l("Choosing collections in interviews", 12, "Justifying your choice with complexity"),
                lab("The comparisons they love", 14, "array:arraylist", "HashMap vs Hashtable, ArrayList vs LinkedList and more"),
                l("Tricky output questions", 12, "Predicting what code prints, and why"))
            .addSection("Multithreading",
                lab("Threads, synchronization and deadlock", 16, "threads:deadlock", "Race conditions, synchronized, volatile, deadlock prevention"),
                lab("Executors, CompletableFuture and concurrent collections", 16, "threadpool:bounded", "Thread pools and async composition"),
                l("Virtual threads: what to say today", 8, "When they help and when they don't"))
            .addSection("The JVM",
                lab("Memory areas, garbage collection and tuning", 14, "memory:gc", "Heap, stack, metaspace and collectors"),
                l("Class loading and memory leaks", 10, "Finding and fixing leaks"))
            .addSection("Spring and Spring Boot",
                l("IoC, beans, scopes and lifecycle", 14, "Dependency injection explained simply"),
                l("Auto-configuration, profiles and starters", 12, "What Spring Boot does at startup"),
                l("@Transactional pitfalls", 12, "Self-invocation, propagation and rollback rules"),
                l("Spring Security and JWT questions", 12, "Filters, authentication and authorization"))
            .addSection("JPA and databases",
                l("Hibernate: entity states, lazy loading and N+1", 14, "The persistence context explained"),
                l("SQL practice: joins, indexes and window functions", 18, "Queries you'll be asked to write"),
                l("Transactions and isolation levels", 12, "Dirty reads, phantoms and what databases actually do"))
            .addSection("Microservices and system design",
                l("Microservices questions", 14, "Communication, consistency, resilience"),
                l("System design basics for backend roles", 16, "Scaling, caching, queues and databases"),
                l("The low-level design round: approach and a full example", 18, "Designing a parking lot step by step"))
            .addSection("The coding round",
                l("The 12 patterns behind most coding questions", 18, "Recognising which pattern fits"),
                l("Solving 10 frequent problems in Java, live", 30, "Thinking aloud, testing and edge cases"),
                l("Writing clean code under pressure", 8, "Naming, structure and handling edge cases"))
            .addSection("Mock interviews and the offer",
                l("Mock interview: 2 years of experience", 25, "A full technical round with feedback"),
                l("Mock interview: 5 years of experience", 30, "Deeper questions, design and trade-offs"),
                l("Behavioural questions and salary negotiation", 12, "Stories that work and how to discuss numbers"));
    }

    // ------------------------------------------------------------------------------------------------
    private static Course dsaInJava() {
        return course("dsa-in-java", "DSA in Java: Crack Coding Rounds with 15 Patterns",
                "Stop memorising solutions. Learn the 15 patterns behind most coding-interview problems, implement them in clean Java, and practise under real interview conditions.",
                """
                ## What you'll learn
                Instead of hundreds of unrelated problems, you learn **15 patterns**: two pointers, sliding window, hashing, binary search on the answer, monotonic stacks, backtracking, BFS and DFS, topological sort, union-find, dynamic programming and more. Every pattern comes with a template, worked problems in Java and a practice set from easy to hard.

                ## Who this course is for
                - You can write Java but get stuck when a coding problem doesn't look familiar.
                - You're preparing for product-company or startup coding rounds.
                - You want Java-specific skills: the right collections, comparators and avoiding common slowdowns.

                ## Not for you if
                - You're new to programming. Learn Java fundamentals first (free on this site).
                - You're aiming at competitive-programming contests. This course targets interviews.

                ## Prerequisites
                - Java basics: loops, arrays, methods, classes and the common collections.

                ## Why it's worth it
                - Pattern thinking transfers to problems you've never seen, which memorising doesn't.
                - Every solution is explained from brute force to optimal, with complexity.
                - Timed mock rounds show you how to think aloud, test and recover when stuck.
                """,
                "Intermediate", 1499, 40,
                "Analyse time and space complexity quickly and correctly",
                "Recognise which of 15 patterns fits a new problem",
                "Implement arrays, strings, stacks, trees, heaps and graphs solutions in clean Java",
                "Solve dynamic programming problems by building from recursion",
                "Use Java collections and comparators effectively in coding rounds",
                "Communicate your approach clearly in a live interview")
            .addSection("Start here",
                free("How to use this course and practise effectively", 8, "The practice loop that makes patterns stick"),
                free("Big-O, the practical way", 14, "Estimating complexity from constraints"),
                free("The Java toolkit for coding rounds", 14, "Collections, comparators, StringBuilder and fast input"))
            .addSection("Arrays and strings",
                lab("Two pointers", 16, "array:two-pointers", "Opposite ends and fast/slow variants"),
                lab("Sliding window", 18, "array:sliding-window", "Fixed and variable windows"),
                l("Prefix sums and difference arrays", 14, "Range sums and subarray counts"),
                l("Hashing patterns", 16, "Counting, grouping and complements"))
            .addSection("Searching and sorting",
                lab("Binary search and binary search on the answer", 18, "array:binary-search", "Finding boundaries and minimising the maximum"),
                l("Sorting-based patterns and custom comparators", 12, "Sorting to simplify problems"))
            .addSection("Stacks, queues and linked lists",
                l("Stacks and monotonic stacks", 16, "Next greater element and histogram problems"),
                l("Queues, deques and the sliding-window maximum", 12, "ArrayDeque patterns"),
                l("Linked lists: fast/slow pointers and reversal", 16, "Cycles, middles and in-place reversal"))
            .addSection("Recursion and backtracking",
                lab("Thinking recursively", 12, "memory:recursion", "Base cases and trusting the recursion"),
                l("Subsets, permutations and combinations", 18, "One template for all three"),
                l("Constraint problems: N-Queens and Sudoku", 14, "Pruning the search"))
            .addSection("Trees",
                l("Traversals: DFS and BFS", 16, "Recursive and iterative"),
                l("Binary search trees", 14, "Validation, search and ordered traversal"),
                l("Tree problems interviewers love", 18, "Lowest common ancestor, diameter, views and paths"))
            .addSection("Heaps",
                l("PriorityQueue patterns", 16, "Top-k, merging k lists and the running median"))
            .addSection("Graphs",
                l("Representations, BFS and DFS", 16, "Grids and adjacency lists"),
                l("Topological sort", 12, "Ordering tasks with dependencies"),
                l("Shortest paths with Dijkstra", 14, "Weighted graphs with PriorityQueue"),
                l("Union-find", 12, "Connected components and cycle detection"))
            .addSection("Dynamic programming",
                l("From recursion to DP: memoisation and tabulation", 16, "Turning brute force into DP step by step"),
                l("1D dynamic programming", 16, "Climbing stairs to house robber"),
                l("2D dynamic programming and grids", 16, "Paths, edit distance and LCS"),
                l("Knapsack and subsequences", 18, "0/1 knapsack, subset sum and LIS"))
            .addSection("Greedy, intervals and more",
                l("Greedy and interval problems", 14, "Merging, scheduling and proving greedy choices"),
                l("Tries", 12, "Prefix search and word problems"),
                l("Bit manipulation essentials", 10, "Masks, XOR tricks and counting bits"))
            .addSection("Mock rounds",
                l("Solving an unseen problem out loud", 20, "Clarify, plan, code, test"),
                l("Three timed mock coding rounds with walkthroughs", 40, "Realistic problems and honest reviews"));
    }

    // ------------------------------------------------------------------------------------------------
    private static Course lowLevelDesign() {
        return course("low-level-design-java", "Low-Level Design in Java: From SOLID to Real Interview Problems",
                "Design real systems the way senior engineers do: requirements, classes, patterns, concurrency and extensibility, practised on ten classic interview problems with complete Java code.",
                """
                ## What you'll learn
                A repeatable six-step approach to design problems, the principles and patterns that actually matter, and ten complete case studies in Java: a parking lot, LRU cache, rate limiter, Splitwise, movie ticket booking with seat locking, an elevator system, a food-delivery order lifecycle, a logging framework, a vending machine and a library system.

                ## Who this course is for
                - Developers with some experience facing low-level design (machine coding) rounds.
                - You know OOP definitions but struggle to turn requirements into a clean class design.
                - You want to write code that's easy to extend at work, not just in interviews.

                ## Not for you if
                - You haven't learned OOP yet. The free OOP lessons on this site come first.
                - You're preparing only for high-level system design (distributed systems at scale).

                ## Prerequisites
                - Comfortable Java, including interfaces, collections and basic multithreading.

                ## Why it's worth it
                - Every case study goes from requirements to working, tested Java code.
                - Concurrency is handled properly (seat locking, rate limiting), which is where most candidates fail.
                - You learn how interviewers score design rounds, so you know what to focus on.
                """,
                "Advanced", 1999, 50,
                "Turn vague requirements into entities, classes and clear responsibilities",
                "Apply SOLID and composition to keep designs easy to change",
                "Use the design patterns that matter: strategy, factory, builder, observer, state, decorator",
                "Make designs thread-safe where it counts",
                "Solve ten classic LLD interview problems with complete Java code",
                "Structure and present a design within a 60-minute round")
            .addSection("Start here",
                free("What the LLD round tests and how it's scored", 10, "What interviewers look for, minute by minute"),
                free("A repeatable six-step approach", 12, "Requirements, entities, relationships, APIs, patterns, concurrency"))
            .addSection("Principles in practice",
                l("SOLID with before-and-after code", 18, "Each principle fixing a real design problem"),
                l("Composition, interfaces and immutability in design", 12, "Designs that are easy to change and test"),
                l("UML: just enough class and sequence diagrams", 10, "Drawing designs quickly on a whiteboard"))
            .addSection("Patterns that matter",
                l("Strategy and factory", 14, "Swapping behaviour and creating objects cleanly"),
                l("Builder and immutable objects", 10, "Readable construction of complex objects"),
                l("Observer and events", 12, "Decoupling notifications"),
                l("State machines", 14, "Modelling lifecycles such as orders and elevators"),
                l("Decorator and chain of responsibility", 14, "Adding behaviour without subclass explosions"),
                l("Singleton, and why to avoid it", 8, "Safe versions and better alternatives"))
            .addSection("Case studies",
                free("Parking lot", 22, "Requirements, classes, pricing strategies and the full code"),
                l("LRU cache", 14, "HashMap plus a doubly linked list, then thread safety"),
                l("Rate limiter", 18, "Token bucket and sliding window, per user"),
                l("Splitwise", 20, "Expenses, splits and simplifying debts"),
                l("Movie ticket booking with seat locking", 24, "Holding seats safely under concurrency"),
                l("Elevator system", 20, "Scheduling strategies and state"),
                l("Food delivery order lifecycle", 18, "A state machine with events"),
                l("Logging framework", 16, "Levels, appenders and chain of responsibility"),
                l("Vending machine", 14, "The state pattern end to end"),
                l("Library management", 14, "Entities, rules and fines"))
            .addSection("Concurrency in design",
                lab("Thread-safe designs", 16, "threads:atomic", "Locks, atomics and immutable state"),
                lab("Handling concurrent bookings", 14, "threads:sync", "Optimistic and pessimistic approaches in code"))
            .addSection("Interview practice",
                l("Two full mock LLD interviews", 45, "Real-time designs with feedback"),
                l("Common mistakes and how to avoid them", 10, "What costs candidates the offer"));
    }
}

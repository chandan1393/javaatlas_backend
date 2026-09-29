package com.javaatlas.course;

import java.util.List;

/**
 * Starter courses added on first start (see CourseSeeder). Edit them, unpublish them,
 * or add video links from the admin screen at /admin.
 * Lecture text supports: ## headings, "- " bullets, "1. " numbered lists, `inline code`, **bold**
 * and ``` fenced code blocks.
 */
final class SeedCourses {

    private SeedCourses() {
    }

    static List<Course> all() {
        return List.of(javaInAnHour(), springBootApi(), jpaClinic(), interviewSprint());
    }

    private static Course course(String slug, String title, String subtitle, String description, String level,
                                 int priceInr, int order, String... outcomes) {
        Course c = Course.create(slug);
        c.update(slug, title, subtitle, description, List.of(outcomes), level, priceInr, true, order);
        return c;
    }

    // ------------------------------------------------------------------ free
    private static Course javaInAnHour() {
        return course("java-in-an-hour", "Java in an hour",
                "A free quick start: install Java, write real programs, and understand what you wrote.",
                "Four short lessons that take you from an empty folder to a working Java program with classes, lists and loops. "
                        + "A good warm-up before the full free lesson path, and a way to try how courses work on JavaAtlas.",
                "Beginner", 0, 0,
                "Install a JDK and run Java programs from the terminal",
                "Use variables, types and if/else decisions",
                "Write classes with fields, constructors and methods",
                "Process data with lists, maps and loops")
            .addSection("Getting started",
                Lecture.of("Install the JDK and run your first program", 10, true, """
                    ## What you need
                    - A **JDK** (Java Development Kit). Install a current long-term support release such as Java 25 from Adoptium (Eclipse Temurin).
                    - An editor. IntelliJ IDEA Community Edition is free and made for Java.

                    ## Check the install
                    Open a terminal and run both commands. They should print the same version.

                    ```bash
                    java -version
                    javac -version
                    ```

                    ## Your first program
                    Create a file named `Hello.java`:

                    ```java
                    public class Hello {
                        public static void main(String[] args) {
                            System.out.println("Hello, Java!");
                        }
                    }
                    ```

                    Run it straight from the source file (Java 11 and later):

                    ```bash
                    java Hello.java
                    ```

                    ## What just happened
                    The compiler turned your source code into **bytecode**, and the **JVM** ran it. The same bytecode runs on Windows, macOS and Linux, which is why Java is called "write once, run anywhere".
                    """),
                Lecture.of("Variables, types and decisions", 12, false, """
                    ## Variables have types
                    Java checks types before the program runs, so many mistakes are caught early.

                    ```java
                    int age = 28;
                    double price = 499.99;
                    boolean member = true;
                    String name = "Asha";
                    var city = "Pune";          // the compiler infers String
                    ```

                    - Whole numbers: `int` (and `long` for very large values)
                    - Decimals: `double`. For money in real apps, use `BigDecimal`.
                    - Text: `String`, compared with `equals()`, never `==`

                    ## Making decisions

                    ```java
                    int marks = 72;
                    String grade;
                    if (marks >= 85) {
                        grade = "A";
                    } else if (marks >= 60) {
                        grade = "B";
                    } else {
                        grade = "C";
                    }
                    ```

                    When one value chooses between many options, a switch expression is shorter:

                    ```java
                    String kind = switch (day) {
                        case "SAT", "SUN" -> "Weekend";
                        default -> "Weekday";
                    };
                    ```

                    ## Try it
                    Write a program that stores a temperature and prints "Cold", "Pleasant" or "Hot".
                    """))
            .addSection("Building real programs",
                Lecture.of("Classes and objects", 15, false, """
                    ## A class describes a thing
                    A class groups data (fields) with the actions on that data (methods). Each object created from it has its own values.

                    ```java
                    public class BankAccount {
                        private final String owner;
                        private double balance;

                        public BankAccount(String owner) {
                            this.owner = owner;
                        }

                        public void deposit(double amount) {
                            if (amount <= 0) {
                                throw new IllegalArgumentException("Amount must be positive");
                            }
                            balance += amount;
                        }

                        public double getBalance() {
                            return balance;
                        }
                    }
                    ```

                    ```java
                    BankAccount acc = new BankAccount("Meera");
                    acc.deposit(500);
                    System.out.println(acc.getBalance());   // 500.0
                    ```

                    ## Why the fields are private
                    Nobody outside the class can set a negative balance directly. Every change goes through `deposit`, which checks the rules. This is **encapsulation**.

                    ## For plain data, use a record

                    ```java
                    record Student(String name, int marks) {}
                    ```

                    One line gives you a constructor, accessors, `equals`, `hashCode` and `toString`.
                    """),
                Lecture.of("Lists, maps and loops", 15, false, """
                    ## Lists keep order

                    ```java
                    List<String> topics = new ArrayList<>(List.of("Java", "Spring"));
                    topics.add("SQL");
                    for (String t : topics) {
                        System.out.println(t);
                    }
                    ```

                    ## Maps look things up by key

                    ```java
                    Map<String, Integer> marks = new HashMap<>();
                    marks.put("Asha", 91);
                    marks.put("Ravi", 78);
                    int ravi = marks.getOrDefault("Ravi", 0);
                    ```

                    ## Putting it together
                    Count how often each word appears:

                    ```java
                    String text = "java is fun and java is fast";
                    Map<String, Integer> counts = new TreeMap<>();
                    for (String word : text.split(" ")) {
                        counts.merge(word, 1, Integer::sum);
                    }
                    System.out.println(counts);   // {and=1, fast=1, fun=1, is=2, java=2}
                    ```

                    ## Where to go next
                    You can now read most beginner Java code. Continue with the free lesson path from "Java fundamentals", or pick a course that matches your goal.
                    """));
    }

    // ------------------------------------------------------------------ Spring Boot API
    private static Course springBootApi() {
        return course("spring-boot-rest-api", "Build a production REST API with Spring Boot",
                "Design, build, secure, test and ship a real REST API with Spring Boot, PostgreSQL and Docker.",
                "You'll build one real project from start to finish: a course catalog API with validation, a PostgreSQL database, "
                        + "role-based security, automated tests and a Docker image ready to deploy. Every lecture ends with working code "
                        + "you can run, and each step explains the production reasons behind it, not just the annotations.",
                "Intermediate", 1499, 1,
                "Structure a Spring Boot project the way teams do in production",
                "Design REST endpoints with proper status codes and error responses",
                "Persist data with Spring Data JPA and PostgreSQL",
                "Secure endpoints with Spring Security and hashed passwords",
                "Write fast tests with slices and real-database tests with Testcontainers",
                "Package the app with Docker and deploy it")
            .addSection("Foundations",
                Lecture.of("What we're building and how to set up", 12, true, """
                    ## The project
                    A course catalog API: list courses, view one course, create and update courses (admins only). Small enough to finish, real enough to show in interviews.

                    ## Create the project
                    Go to start.spring.io and choose Maven, the latest Spring Boot, Java 21 or 25, and these dependencies:

                    - Spring Web (starter webmvc)
                    - Spring Data JPA and the PostgreSQL driver
                    - Validation
                    - Spring Security
                    - Actuator

                    ## Package by feature
                    Group code by what it does, not by technical layer. It keeps related classes together as the project grows.

                    ```text
                    com.example.catalog
                    ├── CatalogApplication.java
                    ├── course/      CourseController, CourseService, Course, CourseRepository
                    ├── security/    SecurityConfig
                    └── common/      ApiErrors, NotFoundException
                    ```

                    ## Run it
                    ```bash
                    ./mvnw spring-boot:run
                    ```
                    Open http://localhost:8080/actuator/health. You should see `{"status":"UP"}`.
                    """),
                Lecture.of("Your first endpoints", 15, false, """
                    ## DTOs as records
                    Never expose entities directly. Records make clean request and response types.

                    ```java
                    public record CourseResponse(Long id, String title, int priceInr) {}

                    public record CreateCourse(
                            @NotBlank @Size(max = 150) String title,
                            @PositiveOrZero int priceInr) {}
                    ```

                    ## The controller

                    ```java
                    @RestController
                    @RequestMapping("/api/v1/courses")
                    class CourseController {
                        private final CourseService service;
                        CourseController(CourseService service) { this.service = service; }

                        @GetMapping
                        List<CourseResponse> list() { return service.list(); }

                        @GetMapping("/{id}")
                        CourseResponse get(@PathVariable Long id) { return service.get(id); }

                        @PostMapping
                        ResponseEntity<CourseResponse> create(@Valid @RequestBody CreateCourse cmd) {
                            CourseResponse created = service.create(cmd);
                            return ResponseEntity.created(URI.create("/api/v1/courses/" + created.id())).body(created);
                        }
                    }
                    ```

                    ## Status codes that tell the truth
                    - 200 for reads, 201 with a Location header for creates, 204 for deletes
                    - 400 for invalid input, 404 when the resource doesn't exist, 409 for conflicts

                    Keep controllers thin: they translate HTTP to method calls. Business rules live in the service.
                    """))
            .addSection("Data with JPA",
                Lecture.of("Entities, repositories and PostgreSQL", 18, false, """
                    ## Start PostgreSQL

                    ```bash
                    docker run -d --name catalog-db -e POSTGRES_PASSWORD=secret -e POSTGRES_DB=catalog -p 5432:5432 postgres:17
                    ```

                    ```yaml
                    spring:
                      datasource:
                        url: jdbc:postgresql://localhost:5432/catalog
                        username: postgres
                        password: secret
                      jpa:
                        open-in-view: false
                        hibernate:
                          ddl-auto: validate
                    ```

                    Use a migration tool (Flyway or Liquibase) to create tables, and let Hibernate only **validate** the schema.

                    ## The entity

                    ```java
                    @Entity
                    class Course {
                        @Id @GeneratedValue(strategy = GenerationType.SEQUENCE)
                        private Long id;
                        @Column(nullable = false, length = 150)
                        private String title;
                        private int priceInr;
                        @Version
                        private int version;
                        protected Course() {}
                        Course(String title, int priceInr) { this.title = title; this.priceInr = priceInr; }
                    }
                    ```

                    ## The repository

                    ```java
                    interface CourseRepository extends JpaRepository<Course, Long> {
                        List<Course> findByTitleContainingIgnoreCase(String q);
                    }
                    ```

                    `@Version` gives you optimistic locking for free: two admins editing the same course can't silently overwrite each other.
                    """),
                Lecture.of("Validation and consistent errors", 14, false, """
                    ## Validate at the edge
                    Annotate the request record and add `@Valid` to the controller parameter. Without `@Valid`, the annotations are ignored.

                    ## One error format for the whole API
                    Spring supports Problem Details (RFC 9457). Return it from a single advice class:

                    ```java
                    @RestControllerAdvice
                    class ApiErrors {
                        @ExceptionHandler(NotFoundException.class)
                        ProblemDetail notFound(NotFoundException ex) {
                            return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
                        }

                        @ExceptionHandler(MethodArgumentNotValidException.class)
                        ProblemDetail invalid(MethodArgumentNotValidException ex) {
                            ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
                            pd.setTitle("Validation failed");
                            pd.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                                    .map(e -> e.getField() + ": " + e.getDefaultMessage()).toList());
                            return pd;
                        }
                    }
                    ```

                    Front ends can now show every error the same way, and you never leak stack traces to users.
                    """))
            .addSection("Security and quality",
                Lecture.of("Securing endpoints with Spring Security", 18, false, """
                    ## Public reads, protected writes

                    ```java
                    @Bean
                    SecurityFilterChain api(HttpSecurity http) throws Exception {
                        http
                            .csrf(csrf -> csrf.disable())
                            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                            .authorizeHttpRequests(auth -> auth
                                .requestMatchers(HttpMethod.GET, "/api/v1/courses/**").permitAll()
                                .requestMatchers("/api/v1/courses/**").hasRole("ADMIN")
                                .anyRequest().authenticated())
                            .httpBasic(Customizer.withDefaults());
                        return http.build();
                    }

                    @Bean
                    PasswordEncoder passwordEncoder() {
                        return new BCryptPasswordEncoder();
                    }
                    ```

                    ## Rules worth remembering
                    - Store only password **hashes** (BCrypt or Argon2), never the password.
                    - Order matters: specific matchers first, `anyRequest()` last.
                    - Disable CSRF only for stateless APIs that don't use cookies. If you use a session cookie, keep CSRF protection or use SameSite=Strict cookies.
                    - For browser apps, prefer an HttpOnly cookie over storing tokens in localStorage.
                    """),
                Lecture.of("Testing the API", 16, false, """
                    ## Fast slice tests

                    ```java
                    @WebMvcTest(CourseController.class)
                    class CourseControllerTest {
                        @Autowired MockMvc mvc;
                        @MockitoBean CourseService service;

                        @Test
                        void rejectsBlankTitle() throws Exception {
                            mvc.perform(post("/api/v1/courses")
                                    .with(user("admin").roles("ADMIN"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\\"title\\":\\"\\",\\"priceInr\\":100}"))
                               .andExpect(status().isBadRequest());
                        }
                    }
                    ```

                    ## Real database tests

                    ```java
                    @DataJpaTest
                    @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
                    @Testcontainers
                    class CourseRepositoryTest {
                        @Container @ServiceConnection
                        static PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:17");

                        @Autowired CourseRepository repo;

                        @Test
                        void findsByTitle() {
                            repo.save(new Course("Spring Boot", 1499));
                            assertThat(repo.findByTitleContainingIgnoreCase("spring")).hasSize(1);
                        }
                    }
                    ```

                    Aim for many fast unit and slice tests, and a few full integration tests for the flows that make money.
                    """))
            .addSection("Ship it",
                Lecture.of("Docker and deployment", 15, false, """
                    ## A small, safe image

                    ```dockerfile
                    FROM maven:3.9-eclipse-temurin-25 AS build
                    WORKDIR /src
                    COPY pom.xml .
                    RUN mvn -q dependency:go-offline
                    COPY src src
                    RUN mvn -q package -DskipTests

                    FROM eclipse-temurin:25-jre
                    RUN useradd --system app
                    USER app
                    COPY --from=build /src/target/*.jar /app.jar
                    ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app.jar"]
                    ```

                    ## Configuration from the environment
                    Never bake secrets into the image. Spring maps environment variables automatically: `SPRING_DATASOURCE_URL` sets `spring.datasource.url`.

                    ## Deploy checklist
                    1. Health checks point at `/actuator/health`.
                    2. The database password and other secrets come from the platform's secret settings.
                    3. Logs go to standard output; the platform collects them.
                    4. Run database migrations before the new version takes traffic.
                    5. Test the deployed app with the same requests you use locally.

                    Congratulations: you have a real, deployable API to show in your portfolio.
                    """));
    }

    // ------------------------------------------------------------------ JPA clinic
    private static Course jpaClinic() {
        return course("jpa-performance-clinic", "JPA and Hibernate performance clinic",
                "Find and fix the slow queries that an ORM makes easy to write.",
                "Most slow Spring applications are slow in the database layer, and most of those problems come from a handful of JPA patterns. "
                        + "In this course you make every query visible, then fix N+1 selects, oversized result sets, slow pagination, "
                        + "row-by-row inserts and lost updates, with before-and-after code for each.",
                "Advanced", 1299, 2,
                "See exactly which SQL your code runs, and count it in tests",
                "Remove N+1 queries with fetch joins, entity graphs and batch fetching",
                "Load only the data a screen needs with projections",
                "Paginate large tables without slowing down",
                "Insert and update thousands of rows efficiently",
                "Choose optimistic or pessimistic locking with confidence")
            .addSection("Seeing the problem",
                Lecture.of("Make every query visible", 10, true, """
                    ## You can't fix what you can't see
                    Turn on SQL logging in development:

                    ```yaml
                    logging:
                      level:
                        org.hibernate.SQL: debug
                        org.hibernate.orm.jdbc.bind: trace
                    spring:
                      jpa:
                        properties:
                          hibernate:
                            generate_statistics: true
                    ```

                    Now every statement and its parameters appear in the log, plus a summary of how many statements each session ran.

                    ## Count queries in tests
                    A test that fails when the number of queries grows catches N+1 bugs before production. Libraries such as datasource-proxy or Hypersistence Utils can assert the statement count, for example "this endpoint runs at most 3 SELECTs".

                    ## What to look for
                    - The same SELECT repeated with different ids: N+1
                    - `select *`-style queries loading columns the screen never shows
                    - Queries inside loops
                    - Long-running transactions holding connections
                    """),
                Lecture.of("The N+1 problem, reproduced and fixed", 18, false, """
                    ## Reproduce it

                    ```java
                    List<Course> courses = courseRepository.findAll();          // 1 query
                    courses.forEach(c -> c.getAuthor().getName());              // +1 query per course
                    ```

                    ## Fix 1: fetch join

                    ```java
                    @Query("select c from Course c join fetch c.author")
                    List<Course> findAllWithAuthor();
                    ```

                    ## Fix 2: entity graph on a derived query

                    ```java
                    @EntityGraph(attributePaths = "author")
                    List<Course> findByPublishedTrue();
                    ```

                    ## Fix 3: batch fetching as a safety net

                    ```yaml
                    spring:
                      jpa:
                        properties:
                          hibernate:
                            default_batch_fetch_size: 50
                    ```

                    Hibernate then loads lazy associations 50 at a time with `IN (...)`, turning 1,000 queries into about 20.

                    ## Rules
                    - Never switch to EAGER to hide the problem.
                    - Don't fetch-join two List collections in one query (you get MultipleBagFetchException or a huge cartesian product).
                    """))
            .addSection("Loading the right data",
                Lecture.of("Projections: fetch only what the screen needs", 14, false, """
                    ## Entities are for changing data
                    A list page that shows title and price doesn't need the full entity graph. Load a projection instead.

                    ```java
                    public record CourseCard(Long id, String title, int priceInr, String authorName) {}

                    @Query(\"""
                        select new com.example.catalog.course.CourseCard(c.id, c.title, c.priceInr, a.name)
                        from Course c join c.author a
                        where c.published = true
                        \""")
                    List<CourseCard> findCards();
                    ```

                    ## Why it's faster
                    - Fewer columns travel over the network.
                    - No entities in the persistence context, so no dirty checking and less memory.
                    - One query, no lazy loading surprises in JSON serialization.

                    Use entities when you will modify data; use projections for reads.
                    """),
                Lecture.of("Pagination that scales", 15, false, """
                    ## Offset pagination slows down
                    `LIMIT 20 OFFSET 100000` still reads and throws away 100,000 rows. Page 1 is fast; page 5,000 is not.

                    ## Keyset pagination
                    Remember the last value you showed and continue from there:

                    ```sql
                    SELECT id, title FROM course
                    WHERE id < :lastSeenId
                    ORDER BY id DESC
                    LIMIT 20;
                    ```

                    With an index on the sort column, every page is as fast as the first. Spring Data supports this with `ScrollPosition` and `Window` results.

                    ## A trap: fetch join plus paging
                    Paging a query that fetch-joins a collection makes Hibernate load everything and paginate in memory (it logs a warning). Page the ids first, then fetch the full rows for those ids.

                    ## Page or Slice?
                    `Page` runs an extra COUNT query. For infinite scroll use `Slice`, which only knows whether a next page exists.
                    """))
            .addSection("Writing safely",
                Lecture.of("Batch inserts and updates", 14, false, """
                    ## Why saveAll can be slow
                    Without batching, 10,000 entities means 10,000 round trips. Enable JDBC batching:

                    ```yaml
                    spring:
                      jpa:
                        properties:
                          hibernate:
                            jdbc.batch_size: 50
                            order_inserts: true
                            order_updates: true
                    ```

                    ## IDENTITY disables insert batching
                    Hibernate needs the id immediately after each insert, so it can't batch. Use a sequence with an allocation size:

                    ```java
                    @Id
                    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "course_seq")
                    @SequenceGenerator(name = "course_seq", allocationSize = 50)
                    private Long id;
                    ```

                    ## Keep memory flat in big jobs
                    Flush and clear the persistence context every batch, or entities pile up in memory:

                    ```java
                    if (i % 50 == 0) { em.flush(); em.clear(); }
                    ```

                    For pure bulk changes, a single `@Modifying` JPQL update beats loading entities at all.
                    """),
                Lecture.of("Locking for real bookings", 16, false, """
                    ## The lost update
                    Two admins load the same course, both change the price, the last save wins silently. Add a version column:

                    ```java
                    @Version
                    private int version;
                    ```

                    Now the second save fails with an optimistic lock exception, and you can tell the user to reload.

                    ## When optimistic isn't enough
                    For hot rows, such as the last seats in a live batch, lock the row while you check and update:

                    ```java
                    @Lock(LockModeType.PESSIMISTIC_WRITE)
                    @Query("select b from Batch b where b.id = :id")
                    Batch findForUpdate(@Param("id") Long id);
                    ```

                    ## Choosing
                    - Low contention, web forms: optimistic (`@Version`)
                    - High contention on a few rows: pessimistic, with short transactions
                    - Never call slow external APIs while holding a lock
                    """));
    }

    // ------------------------------------------------------------------ interview sprint
    private static Course interviewSprint() {
        return course("java-interview-sprint", "Java interview sprint",
                "The questions Java backend interviews actually ask, with model answers and follow-ups.",
                "A focused course for developers preparing for Java backend interviews. Each lecture covers one area with the questions "
                        + "interviewers ask most, a model answer you can adapt, and the follow-up questions that separate good answers from great ones.",
                "Intermediate", 999, 3,
                "Structure technical answers clearly under time pressure",
                "Explain core Java, collections and concurrency internals with confidence",
                "Answer Spring Boot and JPA questions with production examples",
                "Walk through a system design question step by step")
            .addSection("Core Java",
                Lecture.of("How to answer technical questions", 8, true, """
                    ## A simple structure: define, show, trade-off
                    1. **Define** the concept in one or two sentences.
                    2. **Show** a small example, ideally from a project you worked on.
                    3. **Trade-off**: when you would not use it, or what it costs.

                    ## Example
                    Question: "What is a HashMap?"

                    - Define: "A hash table that maps keys to values with average O(1) get and put."
                    - Show: "In our pricing service we cached exchange rates in a map keyed by currency code."
                    - Trade-off: "It isn't thread-safe, so for shared caches we used ConcurrentHashMap."

                    ## When you don't know
                    Say what you do know, reason out loud, and say how you would find out. Interviewers value honest reasoning far more than a guessed answer.
                    """),
                Lecture.of("Strings, equality and immutability", 12, false, """
                    ## Q: Why is String immutable?
                    Model answer: for security (paths, URLs and class names can't change after being checked), thread safety without locks, a cached hashCode that makes Strings good map keys, and the string pool.

                    **Follow-up:** "How do you build strings in a loop?" Use `StringBuilder`; each `+` in a loop creates a new String.

                    ## Q: == versus equals()?
                    `==` compares references; `equals()` compares content. Two strings with the same text can be different objects.

                    ## Q: What is the equals/hashCode contract?
                    If `a.equals(b)` is true, their hash codes must be equal. Break it and HashMap and HashSet lose your objects.

                    **Follow-up:** "How would you write equals for a JPA entity?" Use a business key or id-based equals with a constant hashCode, and never include lazy associations.

                    ## Q: Why is Integer caching a trap?
                    `Integer` values from -128 to 127 are cached, so `==` appears to work for small numbers and fails for larger ones. Always use `equals()` for wrappers.
                    """),
                Lecture.of("Collections under the hood", 14, false, """
                    ## Q: How does HashMap work?
                    Keys are hashed to buckets in an array. Collisions share a bucket as a linked list, which becomes a red-black tree at 8 entries (Java 8+). When size passes capacity times 0.75, the table doubles.

                    **Follow-up:** "What if two keys have the same hashCode?" They share a bucket and `equals()` finds the right one.

                    ## Q: ArrayList versus LinkedList?
                    ArrayList: O(1) random access, cache-friendly, the default choice. LinkedList: O(n) access and more memory per element; it rarely wins in practice.

                    ## Q: ConcurrentHashMap versus synchronizedMap?
                    synchronizedMap locks the whole map for every call. ConcurrentHashMap locks per bucket with CAS, so reads rarely block and writes to different buckets run in parallel.

                    ## Q: fail-fast versus fail-safe iterators?
                    Fail-fast iterators (ArrayList, HashMap) throw ConcurrentModificationException when the collection changes during iteration. Fail-safe ones (CopyOnWriteArrayList, concurrent collections) iterate over a snapshot or tolerate changes.
                    """))
            .addSection("Concurrency and the JVM",
                Lecture.of("Threads, locks and virtual threads", 15, false, """
                    ## Q: synchronized versus volatile?
                    synchronized gives mutual exclusion and visibility for a block. volatile gives visibility and ordering for one variable, but `count++` is still not atomic.

                    ## Q: How do you avoid deadlock?
                    Acquire locks in one global order, keep critical sections short, use `tryLock` with a timeout, or avoid nested locks with higher-level utilities.

                    ## Q: What are virtual threads and when do they help?
                    Lightweight threads (final in Java 21) scheduled by the JVM. When one blocks on I/O, its carrier thread is freed. They raise throughput for I/O-bound servers; CPU-bound work gains nothing.

                    **Follow-up:** "Any pitfalls?" Don't pool them; watch ThreadLocal memory; downstream limits still apply, so 10,000 virtual threads can exhaust a 10-connection database pool. Before Java 24, blocking inside synchronized pinned the carrier thread.

                    ## Q: thenApply versus thenCompose?
                    thenApply maps a result with a normal function; thenCompose chains a function that returns another CompletableFuture and flattens it.
                    """),
                Lecture.of("Memory, GC and production issues", 14, false, """
                    ## Q: Heap versus stack?
                    Each thread has a stack of frames with local variables; objects live on the shared heap, managed by the garbage collector.

                    ## Q: Which garbage collector would you choose?
                    G1 by default. ZGC when pause times must stay under a millisecond on large heaps. Parallel for batch jobs that care only about throughput.

                    ## Q: The service is running out of memory. What do you do?
                    1. Confirm with metrics: is heap usage growing steadily after each GC? That suggests a leak.
                    2. Take a heap dump: `jcmd <pid> GC.heap_dump /tmp/heap.hprof`.
                    3. Open it in Eclipse MAT and look at the dominator tree.
                    4. Typical causes: caches without eviction, static collections that only grow, listeners never removed, ThreadLocals in pooled threads.

                    ## Q: CPU is at 100%. Next step?
                    Take a few thread dumps a few seconds apart (`jcmd <pid> Thread.print`) and look for the same stack in RUNNABLE threads, or record a profile with Java Flight Recorder.
                    """))
            .addSection("Spring and system design",
                Lecture.of("Spring Boot questions you'll be asked", 15, false, """
                    ## Q: How does auto-configuration work?
                    Boot loads candidate configuration classes and applies each only if its conditions pass (class on the classpath, property set, bean missing). Your own beans win because of `@ConditionalOnMissingBean`.

                    ## Q: Why doesn't @Transactional work when I call the method from the same class?
                    Spring applies it through a proxy. A call through `this` skips the proxy, so no transaction starts.

                    ## Q: What is the N+1 problem and how do you fix it?
                    One query loads a list, then one query per row loads an association. Fix with join fetch, entity graphs, projections or batch fetching.

                    ## Q: Constructor or field injection?
                    Constructor: dependencies are explicit and final, tests are simple, and circular dependencies fail at startup.

                    ## Q: How do you handle errors in a REST API?
                    A `@RestControllerAdvice` maps exceptions to one response format, such as Problem Details, with correct status codes.
                    """),
                Lecture.of("Designing a URL shortener", 20, false, """
                    ## Step 1: requirements (2 minutes)
                    Create a short link, redirect quickly, track click counts. Assume 100 million new links a month and 10 times more reads than writes.

                    ## Step 2: API

                    ```text
                    POST /links        { "url": "..." }  ->  { "code": "aZ3kP9" }
                    GET  /{code}       ->  301/302 redirect
                    ```

                    ## Step 3: generating codes
                    Base62 of a unique id: 7 characters give about 3.5 trillion codes. Get ids from a database sequence or pre-allocated id ranges per server, so servers don't collide.

                    ## Step 4: storage and reads
                    A simple table (code, url, created_at). Put a cache such as Redis in front for hot codes; most traffic hits a small set of links.

                    ## Step 5: scale and trade-offs
                    - 301 is cached by browsers (fewer requests, less accurate click counts); 302 is the opposite.
                    - Count clicks asynchronously through a queue so redirects stay fast.
                    - Rate-limit creation to stop abuse.

                    Talk through trade-offs out loud. The interviewer grades your reasoning, not one "correct" diagram.
                    """));
    }
}

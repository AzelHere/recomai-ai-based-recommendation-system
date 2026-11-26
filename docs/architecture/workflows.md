# Recom AI implementation workflows

## Frontend to backend

```mermaid
flowchart LR
n0_0["Browser ES modules"] --> n0_1["Same-origin JSON API"] --> n0_2["MVC controller"] --> n0_3["Service"] --> n0_4["JDBC DAO"] --> n0_5["MySQL"]
```

## Spring MVC request lifecycle

```mermaid
flowchart LR
n0_0["HTTP request"] --> n0_1["Request context and security headers"] --> n0_2["Controller binding"] --> n0_3["SessionAccess"] --> n0_4["Service validation"] --> n0_5["DAO"] --> n0_6["DTO response"]
```

## Java package architecture

```mermaid
flowchart LR
n0_0["controller"] --> n0_1["service"] --> n0_2["dao"] --> n0_3["model"]
n1_0["service"] --> n1_1["recommendation"]
n2_0["controller"] --> n2_1["web"]
n3_0["service"] --> n3_1["catalog"]
```

## Recommendation workflow

```mermaid
flowchart LR
n0_0["Owner preferences"] --> n0_1["Bounded SQL candidates"] --> n0_2["Content cosine"] --> n0_3["Hybrid score"] --> n0_4["Familiar related discovery allocation"] --> n0_5["Near-tie diversity"] --> n0_6["Persisted batch"] --> n0_7["Explanation"]
```

## TF-IDF and cosine

```mermaid
flowchart LR
n0_0["Catalog metadata"] --> n0_1["Unicode tokens"] --> n0_2["Smoothed IDF and logarithmic TF"] --> n0_3["L2 normalization"] --> n0_4["Positive history plus selected topics"] --> n0_5["Dot product"] --> n0_6["Top overlap terms"]
```

## Hybrid scoring

```mermaid
flowchart LR
n0_0["Clamped candidate signals"] --> n0_1["Weighted positive points"] --> n0_2["Bounded negative deduction"] --> n0_3["Stable item-ID tie break"] --> n0_4["Bucket allocation"] --> n0_5["Feed"]
```

## User workflow

```mermaid
flowchart LR
n0_0["Register"] --> n0_1["Goal"] --> n0_2["Area and topics"] --> n0_3["For You"] --> n0_4["Explore"] --> n0_5["Save"] --> n0_6["Journey"] --> n0_7["Explicit progress"] --> n0_8["Feedback"]
```

## Learning journey workflow

```mermaid
flowchart LR
n0_0["Authenticated owner"] --> n0_1["Validate request and lock user"] --> n0_2["Select active candidates"] --> n0_3["Topic preselection"] --> n0_4["Goal and difficulty order"] --> n0_5["Transactional plan"] --> n0_6["Explicit step progress"]
```

## Educational ingestion workflow

```mermaid
flowchart LR
n0_0["Reviewed fixed publishers"] --> n0_1["Bounded metadata acquisition"] --> n0_2["Licensed attributed JSON"] --> n0_3["Admin preview"] --> n0_4["Digest revalidation"] --> n0_5["Source locks"] --> n0_6["Transactional batch"] --> n0_7["After-commit cache invalidation"]
```

## Authentication and authorization

```mermaid
flowchart LR
n0_0["Credentials"] --> n0_1["Salted password verification"] --> n0_2["Rotate session"] --> n0_3["Persisted auth-version check"] --> n0_4["Owner or admin authorization"] --> n0_5["Scoped service operation"]
```

## Testing architecture

```mermaid
flowchart LR
n0_0["JUnit and MVC slices"] --> n0_1["JaCoCo report"]
n1_0["Playwright"] --> n1_1["Isolated Compose application"] --> n1_2["Disposable test database"]
n2_0["SpotBugs"] --> n2_1["Compiled Java bytecode"]
```

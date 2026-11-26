# Recom AI API

[Route reference](../technical/API-Reference.md) · [DTO contracts](../technical/API-DTO-Contracts.java.txt) · [controllers](../../backend/src/main/java/com/guvi/recommendationsystem/controller/).

JSON endpoints share the application origin. `/api/health` reports service/database health. `/api/items/search` provides page/pageSize, query, categoryId, type, difficulty, maximumTime, topic, provider and sort filters. ARTICLE includes clearly identified textbook reading sections. Unknown duration is excluded from a maximum-time filter. Unsupported types return a validation error.

`/api/auth/*` manages sessions and password reset; `/api/users/me/*` uses the authenticated owner, not a caller-supplied user ID. Admin catalog/import/configuration endpoints require an administrator. Preview never imports; commit revalidates the exact digest. Errors return safe messages and correlation IDs rather than SQL or stack traces. Refer to controllers and DTO contracts for exact methods and payloads.

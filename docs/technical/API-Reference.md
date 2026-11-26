# API reference

This reference comes from every actual Spring MVC controller mapping. Java signatures below are the authoritative request parameter names, defaults, optional flags and response types. Request/response records are reproduced in `API-DTO-Contracts.java.txt`; generic wrapper names remain meaningful Java contracts rather than invented JSON fields. Examples are illustrative unless explicitly marked captured. Same-origin session cookies authenticate owner/admin operations; never place a user ID in a URL to impersonate another account.

## Shared behavior
JSON uses camelCase record fields. Missing/invalid bindings and validation return 400; anonymous owner/admin access returns 401, member access to admin returns 403, missing records 404, stale revisions 409, wrong method 405, unsupported media 415, throttling 429, database unavailability 503. Errors contain status, error, message, path and timestamp; unexpected details stay private. Foreign/null Origin or cross-site Fetch Metadata rejects unsafe API mutations with 403. Owner checks can intentionally return 404 for another user's object. Request IDs support logs without exposing raw tokens.

Paged endpoints return `CatalogPage<T>` with items, page, pageSize, totalItems and totalPages. See each service for endpoint-specific limits; catalog/admin are 1-based, default 20, maximum 100. Legacy list APIs remain unpaged compatibility endpoints. Updates with X-Expected-Revision are optimistic concurrency guards where signatures expose the header. Request-body shapes are records, not arbitrary maps except where explicitly typed Map.

## Safe examples
Captured anonymous reads: `GET /api/health`, `GET /api/categories`, `GET /api/items/search?page=1&pageSize=2`. See `evidence/public-api-examples.json` for actual public responses. Example authenticated call: `curl --cookie <private-cookie-file> http://localhost:8080/api/users/me/journeys?page=1`. This is a syntax example, not a published cookie. For import, preview first, then commit the same resources with the returned expectedSha256. No example session or reset token is shipped.

## GET /api/items/{id}/provenance

Purpose: `provenance`. Access: **Public / no session required**. Source: `backend/src/main/java/com/guvi/recommendationsystem/catalog/IngestionController.java:16`.

```java
public Map<String,Object> provenance(@PathVariable long id)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/admin/ingestion

Purpose: `overview`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/catalog/IngestionController.java:18`.

```java
public Map<String,Object> overview(HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## POST /api/admin/ingestion/preview

Purpose: `preview`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/catalog/IngestionController.java:20`.

```java
public CatalogIngestionService.Preview preview(@RequestBody ImportRequest input,HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## POST /api/admin/ingestion/commit

Purpose: `commit`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/catalog/IngestionController.java:22`.

```java
public CatalogIngestionService.ImportResult commit(@RequestBody ImportRequest input,HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## POST /api/admin/ingestion/check-urls

Purpose: `check`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/catalog/IngestionController.java:24`.

```java
public Map<String,Object> check(HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/admin/analytics/summary

Purpose: `getSummary`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/AdminAnalyticsController.java:25`.

```java
public AdminDashboardSummary getSummary(HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/admin/analytics/insights

Purpose: `getInsights`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/AdminAnalyticsController.java:31`.

```java
public AdminRecommendationInsights getInsights( @RequestParam(defaultValue = "5") int limit, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/admin/analytics/recommendation-engagement

Purpose: `getRecommendationEngagement`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/AdminAnalyticsController.java:38`.

```java
public RecommendationEngagementMetrics getRecommendationEngagement(HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/admin/settings

Purpose: `get`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/AdminSettingsController.java:22`.

```java
public AdminSystemSettings get(HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## PUT /api/admin/settings

Purpose: `update`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/AdminSettingsController.java:25`.

```java
public AdminSystemSettings update(@RequestBody AdminSettingsUpdateRequest body,@RequestHeader(value="X-Expected-Revision",required=false) Long revision, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## POST /api/admin/settings/preview

Purpose: `preview`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/AdminSettingsController.java:28`.

```java
public List<RecommendationDraft> preview(@RequestBody AdminSettingsPreviewRequest body, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/admin/settings/audit

Purpose: `audit`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/AdminSettingsController.java:32`.

```java
public List<AdminSettingsAuditEntry> audit(@RequestParam(defaultValue = "25") int limit, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## POST /api/auth/register

Purpose: `register`. Access: **Public / no session required**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/AuthController.java:37`.

```java
public ResponseEntity<UserProfile> register( @RequestBody RegisterUserRequest request, HttpServletRequest servletRequest)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## POST /api/auth/login

Purpose: `login`. Access: **Public / no session required**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/AuthController.java:46`.

```java
public UserProfile login(@RequestBody LoginRequest request, HttpServletRequest servletRequest)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## POST /api/auth/logout

Purpose: `logout`. Access: **Public / no session required**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/AuthController.java:54`.

```java
public ResponseEntity<Map<String, String>> logout(HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## POST /api/auth/password-reset/request

Purpose: `requestPasswordReset`. Access: **Public / no session required**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/AuthController.java:60`.

```java
public ResponseEntity<Map<String, String>> requestPasswordReset( @RequestBody PasswordResetRequest request, HttpServletRequest servletRequest)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## POST /api/auth/password-reset/complete

Purpose: `completePasswordReset`. Access: **Public / no session required**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/AuthController.java:69`.

```java
public ResponseEntity<Map<String, String>> completePasswordReset( @RequestBody PasswordResetCompletionRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/items/search

Purpose: `search`. Access: **Public / no session required**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/CatalogQueryController.java:12`.

```java
public CatalogPage<CatalogItem> search(@RequestParam(required=false)String query,@RequestParam(required=false)Long categoryId,@RequestParam(required=false)ItemType type,@RequestParam(defaultValue="RELEVANCE")CatalogSort sort,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize,@RequestParam(required=false)ItemDifficulty difficulty,@RequestParam(required=false)Integer maximumTime,@RequestParam(required=false)String provider)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/items/batch

Purpose: `metadata`. Access: **Public / no session required**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/CatalogQueryController.java:14`.

```java
public List<CatalogItem> metadata(@RequestParam List<Long> ids)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/categories

Purpose: `getActiveCategories`. Access: **Public / no session required**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/CategoryController.java:34`.

```java
public List<Category> getActiveCategories()
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/admin/categories

Purpose: `getAllCategories`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/CategoryController.java:39`.

```java
public List<Category> getAllCategories(HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## POST /api/admin/categories

Purpose: `createCategory`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/CategoryController.java:45`.

```java
public ResponseEntity<Category> createCategory( @RequestBody CategoryRequest categoryRequest, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## PUT /api/admin/categories/{categoryId}

Purpose: `updateCategory`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/CategoryController.java:55`.

```java
public Category updateCategory( @PathVariable long categoryId, @RequestBody CategoryRequest categoryRequest, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## PATCH /api/admin/categories/{categoryId}/active

Purpose: `setCategoryActive`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/CategoryController.java:64`.

```java
public Category setCategoryActive( @PathVariable long categoryId, @RequestBody ActiveStatusRequest statusRequest, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/discovery/niches

Purpose: `getNiches`. Access: **Public / no session required**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/DiscoveryController.java:33`.

```java
public List<NicheOption> getNiches()
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/users/me/onboarding

Purpose: `getOnboarding`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/DiscoveryController.java:38`.

```java
public OnboardingStatus getOnboarding(HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## POST /api/users/me/onboarding

Purpose: `saveOnboarding`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/DiscoveryController.java:43`.

```java
public OnboardingStatus saveOnboarding(@RequestBody OnboardingRequest body, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/users/me/interests/insights

Purpose: `getInterestInsights`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/DiscoveryController.java:48`.

```java
public InterestProfile getInterestInsights(HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/users/me/feedback

Purpose: `getMyFeedback`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/FeedbackController.java:31`.

```java
public List<Feedback> getMyFeedback(HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## POST /api/users/me/feedback

Purpose: `createFeedback`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/FeedbackController.java:36`.

```java
public ResponseEntity<Feedback> createFeedback( @RequestBody FeedbackRequest body, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## PUT /api/users/me/feedback/{itemId}

Purpose: `updateFeedback`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/FeedbackController.java:43`.

```java
public Feedback updateFeedback( @PathVariable long itemId, @RequestBody FeedbackRequest body, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/health

Purpose: `health`. Access: **Public / no session required**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/HealthController.java:17`.

```java
public Map<String, String> health()
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## POST /api/users/me/interactions

Purpose: `track`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/InteractionController.java:32`.

```java
public ResponseEntity<Interaction> track( @RequestBody TrackInteractionRequest interactionRequest, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## POST /api/users/me/interactions/search

Purpose: `trackSearch`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/InteractionController.java:42`.

```java
public ResponseEntity<Interaction> trackSearch( @RequestBody SearchInteractionRequest searchRequest, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/users/me/interactions

Purpose: `getRecent`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/InteractionController.java:51`.

```java
public List<Interaction> getRecent( @RequestParam(defaultValue = "50") int limit, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/users/me/interactions/progress

Purpose: `getCurrentProgress`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/InteractionController.java:58`.

```java
public List<Interaction> getCurrentProgress(HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## DELETE /api/users/me/interactions

Purpose: `clearHistory`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/InteractionController.java:63`.

```java
public ResponseEntity<Void> clearHistory(HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/interests

Purpose: `getActiveInterests`. Access: **Public / no session required**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/InterestController.java:41`.

```java
public List<Interest> getActiveInterests()
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/admin/interests

Purpose: `getAllInterests`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/InterestController.java:46`.

```java
public List<Interest> getAllInterests(HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## POST /api/admin/interests

Purpose: `createInterest`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/InterestController.java:52`.

```java
public ResponseEntity<Interest> createInterest( @RequestBody InterestRequest interestRequest, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## PUT /api/admin/interests/{interestId}

Purpose: `updateInterest`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/InterestController.java:60`.

```java
public Interest updateInterest( @PathVariable long interestId, @RequestBody InterestRequest interestRequest, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## PATCH /api/admin/interests/{interestId}/active

Purpose: `setInterestActive`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/InterestController.java:69`.

```java
public Interest setInterestActive( @PathVariable long interestId, @RequestBody ActiveStatusRequest requestBody, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## PUT /api/admin/items/{itemId}/interests/{interestId}

Purpose: `associateItemInterest`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/InterestController.java:83`.

```java
public ResponseEntity<Void> associateItemInterest( @PathVariable long itemId, @PathVariable long interestId, @RequestBody InterestWeightRequest body, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## DELETE /api/admin/items/{itemId}/interests/{interestId}

Purpose: `removeItemInterest`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/InterestController.java:94`.

```java
public ResponseEntity<Void> removeItemInterest( @PathVariable long itemId, @PathVariable long interestId, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/admin/items/search

Purpose: `adminSearch`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/ItemController.java:45`.

```java
public CatalogPage<CatalogItem> adminSearch(@RequestParam(defaultValue="") String query, @RequestParam(required=false) Long categoryId,@RequestParam(defaultValue="") String status, @RequestParam(defaultValue="title") String sort,@RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize,HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/admin/items/composition

Purpose: `adminComposition`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/ItemController.java:54`.

```java
public List<java.util.Map<String,Object>> adminComposition(HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/items

Purpose: `getActiveItems`. Access: **Public / no session required**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/ItemController.java:65`.

```java
public List<CatalogItem> getActiveItems(@RequestParam(required = false) Long categoryId)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/items/{itemId}

Purpose: `getActiveItem`. Access: **Public / no session required**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/ItemController.java:73`.

```java
public CatalogItem getActiveItem(@PathVariable long itemId)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/admin/items

Purpose: `getAllItems`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/ItemController.java:82`.

```java
public List<CatalogItem> getAllItems(HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## POST /api/admin/items

Purpose: `createItem`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/ItemController.java:88`.

```java
public ResponseEntity<CatalogItem> createItem( @RequestBody ItemRequest itemRequest, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## PUT /api/admin/items/{itemId}

Purpose: `updateItem`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/ItemController.java:97`.

```java
public CatalogItem updateItem( @PathVariable long itemId, @RequestBody ItemRequest itemRequest, @org.springframework.web.bind.annotation.RequestHeader(value="X-Expected-Revision",required=false) Long revision, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## PATCH /api/admin/items/{itemId}/active

Purpose: `setItemActive`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/ItemController.java:107`.

```java
public CatalogItem setItemActive( @PathVariable long itemId, @RequestBody ActiveStatusRequest statusRequest, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## PATCH /api/admin/items/active

Purpose: `setItemsActive`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/ItemController.java:121`.

```java
public BulkActiveStatusResult setItemsActive( @RequestBody BulkActiveStatusRequest requestBody, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## POST /api/admin/items/import/preview

Purpose: `previewImport`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/ItemController.java:131`.

```java
public CatalogCsvPreview previewImport(@RequestBody CatalogCsvRequest body, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## POST /api/admin/items/import/commit

Purpose: `commitImport`. Access: **ADMIN session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/ItemController.java:137`.

```java
public CatalogCsvImportResult commitImport(@RequestBody CatalogCsvRequest body, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/users/me/journeys

Purpose: `list`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/LearningJourneyController.java:14`.

```java
public CatalogPage<LearningJourneyService.Header> list(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize,HttpServletRequest r)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## POST /api/users/me/journeys

Purpose: `create`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/LearningJourneyController.java:15`.

```java
public LearningJourneyService.Journey create(@RequestBody LearningJourneyService.CreateRequest body,HttpServletRequest r)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/users/me/journeys/{id}

Purpose: `get`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/LearningJourneyController.java:16`.

```java
public LearningJourneyService.Journey get(@PathVariable long id,HttpServletRequest r)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## PATCH /api/users/me/journeys/{id}/steps/{stepId}

Purpose: `step`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/LearningJourneyController.java:17`.

```java
public LearningJourneyService.Journey step(@PathVariable long id,@PathVariable long stepId,@RequestBody StepRequest body,HttpServletRequest r)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## POST /api/users/me/journeys/{id}/archive

Purpose: `archive`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/LearningJourneyController.java:18`.

```java
public LearningJourneyService.Journey archive(@PathVariable long id,HttpServletRequest r)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## POST /api/users/me/journeys/{id}/replace-unavailable

Purpose: `replace`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/LearningJourneyController.java:19`.

```java
public LearningJourneyService.Journey replace(@PathVariable long id,HttpServletRequest r)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## DELETE /api/users/me/journeys/{id}

Purpose: `delete`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/LearningJourneyController.java:20`.

```java
public void delete(@PathVariable long id,HttpServletRequest r)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/users/me/recommendations/exploration

Purpose: `exploration`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/RecommendationController.java:34`.

```java
public ExplorationPreference exploration(HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## POST /api/users/me/recommendations

Purpose: `generate`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/RecommendationController.java:48`.

```java
public ResponseEntity<List<PersonalizedRecommendation>> generate( @RequestParam(defaultValue = "10") int limit, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/users/me/recommendations

Purpose: `getLatest`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/RecommendationController.java:58`.

```java
public List<PersonalizedRecommendation> getLatest( @RequestParam(defaultValue = "10") int limit, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## POST /api/users/me/recommendations/{recommendationId}/impression

Purpose: `recordImpression`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/RecommendationController.java:65`.

```java
public ResponseEntity<Void> recordImpression(@PathVariable long recommendationId, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## POST /api/users/me/recommendations/{recommendationId}/click

Purpose: `recordClick`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/RecommendationController.java:71`.

```java
public ResponseEntity<Void> recordClick(@PathVariable long recommendationId, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/users/me/recommendations/discovery

Purpose: `getCatalogDiscovery`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/RecommendationController.java:77`.

```java
public List<CatalogItem> getCatalogDiscovery( @RequestParam(defaultValue = "10") int limit, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/recommendation-info

Purpose: `getInfo`. Access: **Public / no session required**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/RecommendationInfoController.java:18`.

```java
public RecommendationInfo getInfo()
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/users/me/collections

Purpose: `list`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/UserCollectionController.java:17`.

```java
public List<UserCollection> list(HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## POST /api/users/me/collections

Purpose: `create`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/UserCollectionController.java:18`.

```java
public UserCollection create(@RequestBody CollectionRequest body, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## PUT /api/users/me/collections/{collectionId}/items/{itemId}

Purpose: `add`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/UserCollectionController.java:22`.

```java
public void add(@PathVariable long collectionId, @PathVariable long itemId, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## DELETE /api/users/me/collections/{collectionId}/items/{itemId}

Purpose: `remove`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/UserCollectionController.java:26`.

```java
public void remove(@PathVariable long collectionId, @PathVariable long itemId, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## DELETE /api/users/me/collections/{collectionId}

Purpose: `delete`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/UserCollectionController.java:30`.

```java
public void delete(@PathVariable long collectionId, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## PATCH /api/users/me/collections/{collectionId}

Purpose: `rename`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/UserCollectionController.java:32`.

```java
public UserCollection rename(@PathVariable long collectionId,@RequestBody RenameRequest body,HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/users/me

Purpose: `getProfile`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/UserController.java:49`.

```java
public UserProfile getProfile(HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## DELETE /api/users/me

Purpose: `deleteAccount`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/UserController.java:54`.

```java
public ResponseEntity<Void> deleteAccount(HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/users/me/recommendation-profile

Purpose: `getRecommendationProfile`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/UserController.java:62`.

```java
public RecommendationProfile getRecommendationProfile(HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## PATCH /api/users/me/profile

Purpose: `updateProfile`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/UserController.java:67`.

```java
public UserProfile updateProfile( @RequestBody DisplayNameUpdateRequest requestBody, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/users/me/preferences

Purpose: `getPreferences`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/UserController.java:75`.

```java
public List<UserPreference> getPreferences(HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## PUT /api/users/me/preferences/{categoryId}

Purpose: `setPreference`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/UserController.java:80`.

```java
public ResponseEntity<Void> setPreference( @PathVariable long categoryId, @RequestBody PreferenceWeightRequest requestBody, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## PUT /api/users/me/interests/{interestId}

Purpose: `selectInterest`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/UserController.java:92`.

```java
public ResponseEntity<Void> selectInterest( @PathVariable long interestId, @RequestBody UserInterestRequest requestBody, HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/users/me/export

Purpose: `export`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/UserDataController.java:23`.

```java
public ResponseEntity<Map<String, Object>> export(HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## DELETE /api/users/me/personalization

Purpose: `resetPersonalization`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/UserDataController.java:30`.

```java
public ResponseEntity<Void> resetPersonalization(HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/users/me/saved

Purpose: `saved`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/WorkspaceController.java:16`.

```java
public CatalogPage<CatalogItem> saved(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize,@RequestParam(required=false)String query,@RequestParam(required=false)Long collectionId,HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## DELETE /api/users/me/saved/{itemId}

Purpose: `unsave`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/WorkspaceController.java:18`.

```java
public void unsave(@PathVariable long itemId,HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.

## GET /api/users/me/activity

Purpose: `activity`. Access: **Owner session**. Source: `backend/src/main/java/com/guvi/recommendationsystem/controller/WorkspaceController.java:20`.

```java
public WorkspaceReadService.ActivityPage activity(@RequestParam(defaultValue="25")int pageSize,@RequestParam(required=false)InteractionType type,@RequestParam(required=false)String cursor,HttpServletRequest request)
```

Parameters and body: exactly as annotated above; `@RequestParam` values are query parameters, `@PathVariable` values replace path braces, `@RequestBody` is JSON and `HttpServletRequest` is server context. Return structure: the declared Java return type, with record fields expanded in the DTO companion. Success is 200 unless `@ResponseStatus`, `ResponseEntity` or a no-content controller response specifies otherwise. Shared errors above apply; service-level bounds, ownership, missing-item and revision validation are enforced by the called service.


## Article filter parameters

GET /api/items/search accepts independent topic (at most 150 characters), provider (exact source name), type=ARTICLE, query, categoryId, difficulty, maximumTime, sort, page and pageSize. Topic intersects search; unknown duration does not pass a maximum-time constraint. GET /api/admin/items/search now binds the same implemented ItemType enum through optional type. Administrative authorization and pagination are unchanged. BOOK and TUTORIAL are unsupported enum values.

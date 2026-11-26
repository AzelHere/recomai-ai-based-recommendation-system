# Reviewed article metadata sources

The application retains genuine titles, official links, short publisher metadata excerpts, provenance and attribution. It does not host complete articles, book chapters, illustrations or third-party media. It does not train an LLM or send provider text to an LLM. Runtime recommendations use local TF-IDF and deterministic scoring.

| Source | Reviewed permission and method | Scope |
|---|---|---|
| OpenStax | [Current licensing guidance](https://help.openstax.org/s/article/Licensing-information-of-OpenStax-textbooks); each book's actual `content.book.license` is checked. CC BY-NC-SA 4.0 and explicitly declared CC BY 4.0 are registered separately. Public book/page paths are allowed by the general robots policy; archive and other disallowed paths are excluded. | Individual numbered explanatory reading sections; title, short publisher description, author names, language, book and original URL. Descriptions explicitly identify textbook sections. No prefaces, chapter summaries, reviews, exercises, assessments or whole-book records are imported as articles. Books can expose older license metadata: its exact declaration is retained rather than inferred from another book. Applicable noncommercial/share-alike conditions remain. |
| Microsoft Learn | [Azure developer documentation source license](https://github.com/MicrosoftDocs/azure-dev-docs/blob/main/LICENSE), CC BY 4.0; [official Java fundamentals table of contents](https://github.com/MicrosoftDocs/azure-dev-docs/blob/main/articles/java/fundamentals/toc.yml). | Six individual explanatory/how-to Java articles linked from the reviewed repository and one specifically allowlisted Azure SQL conceptual comparison, licensed by [azure-docs](https://github.com/MicrosoftDocs/azure-docs/blob/main/LICENSE). No API reference pages, unrelated Learn modules, indexes or code/media copied. Article URLs and metadata come from the provider rather than being generated. |
| MedlinePlus Genetics | [Official XML index and programmatic interface](https://medlineplus.gov/about/developers/geneticsdatafilesapi/); [content use conditions](https://medlineplus.gov/about/using/usingcontent/). | Government-authored Help Me Understand Genetics explanatory articles only. English XML titles/URLs and page metadata; no licensed encyclopedia/drug entries or images. The older 200 MedlinePlus health-topic guides are distinct and retained. |

All records retain their exact source-specific license and original link. Author names are included when supplied by the book metadata; Microsoft contributions and agency authorship are attributed to their actual source. Publication dates, ratings, duration and difficulty are not guessed. The neutral internal quality prior is not a source rating.

Other candidate outcomes:

- OpenLearn: the official feed is explicitly offered, but direct feed and robots requests returned a 403 challenge. No challenge was bypassed; no records imported.
- freeCodeCamp: robots allows access, but the [terms](https://www.freecodecamp.org/news/terms-of-service/) prohibit general automated acquisition. Excluded.
- CDC: initial policy research was accessible, but the shared acquisition client received HTTP 403 from the robots endpoint. No retry with an impersonated browser or bypass was attempted; CDC was removed from the acquisition and registry. General physical/emotional/mental health and sleep readings instead come from the appropriately classified OpenStax College Success health chapter.
- Smarthistory: its FAQ offers CC BY-NC-SA, but the public metadata endpoint returned a challenge. Excluded.
- The Conversation: its robots policy denies the relevant automation agents; no catalog acquisition was performed.
- Oracle, IBM, Python/PostgreSQL/MySQL documentation, RBI, SEBI, World Bank and Smithsonian: not imported in this bounded acquisition. No unreviewed rights claim is made. API reference documentation is not relabeled merely to increase article counts.

The shared `SourceHttp` client enforces reviewed HTTPS hosts, public DNS addresses at the actual socket, original-host TLS verification, bounded redirects, response/deadline/size limits and sequential one-second pacing. No proxies, CAPTCHA bypass, authentication bypass or private destination exceptions were introduced. The host's NAT64 answers were rejected by the existing guard; the operator selected the standard IPv4 Java stack while retaining the same public-address validation.

Raw public source caches remain local and excluded from deliverables. Hash-bound receipt sidecars preserve checked timestamps, HTTP status and final destinations, including cached verification. Availability is time-sensitive. A restriction is not classified as a confirmed missing page.

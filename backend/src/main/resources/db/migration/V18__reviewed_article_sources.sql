-- Add approved metadata sources. Existing catalog rows and owner relationships are unchanged.
INSERT INTO catalog_sources(source_id,name,official_url,license,license_url,attribution_requirement) VALUES
('openstax-readings-ccby','OpenStax','https://openstax.org/','CC-BY-4.0','https://creativecommons.org/licenses/by/4.0/','Credit OpenStax, Rice University and named authors; link the original section and CC BY 4.0 license. Only books whose actual publisher metadata explicitly declares CC BY 4.0; record the source-specific license, do not infer it from other titles.'),
('openstax-readings','OpenStax','https://openstax.org/','CC-BY-NC-SA-4.0','https://help.openstax.org/s/article/Licensing-information-of-OpenStax-textbooks','Credit OpenStax, Rice University and identified authors; name the book/section; link original and license. Noncommercial, share alike. Only metadata and short publisher excerpts, no images or full chapters.'),
('medline-genetics','MedlinePlus Genetics, National Library of Medicine','https://medlineplus.gov/genetics/','US-GOV-PUBLIC-DOMAIN','https://medlineplus.gov/about/using/usingcontent/','Credit MedlinePlus Genetics, National Library of Medicine; only government-authored Help Me Understand Genetics text metadata, no third-party images.'),
('microsoft-learn','Microsoft Learn','https://learn.microsoft.com/','CC-BY-4.0','https://github.com/MicrosoftDocs/azure-docs/blob/main/LICENSE','Credit Microsoft and named contributors; link original and CC BY 4.0. Only reviewed conceptual articles from a repository with this license; no reference APIs or trademarks/images.');

-- These exact unchanged curated entries point to publication/library/home pages, not individual articles.
-- Keep identity, URLs, metadata and owner relationships. Preserve any manually revised entry.
UPDATE items SET item_type='RESOURCE', revision=revision+1
WHERE item_type='ARTICLE' AND revision=0 AND (title,external_url) IN (
 ('The Writing Cooperative','https://writingcooperative.com/'),
 ('National Geographic Photography','https://www.nationalgeographic.com/photography'),
 ('PetaPixel Photography Tutorials','https://petapixel.com/'),
 ('Unsplash Photography Resources','https://unsplash.com/blog/'),
 ('The Muse Career Advice','https://www.themuse.com/advice'),
 ('IMF Finance & Development','https://www.imf.org/en/Publications/fandd'),
 ('FINRA Asset Allocation and Diversification','https://www.finra.org/investors/'),
 ('Y Combinator Startup Library','https://www.ycombinator.com/library'),
 ('Deep Work and Focus','https://www.calnewport.com/books/deep-work/'),
 ('The Lean Startup: Build-Measure-Learn','https://www.strategyzer.com/')
);

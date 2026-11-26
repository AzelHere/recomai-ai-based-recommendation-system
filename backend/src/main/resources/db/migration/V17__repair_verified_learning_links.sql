-- Official replacements reviewed in Phase 4. Preserve IDs, history and manually changed URLs.
-- Google answer 6230175 describes reviews/ratings rather than trip planning.
UPDATE items SET external_url='https://support.google.com/maps/answer/144339',revision=revision+1
WHERE title='Google Maps Trip Planning' AND external_url='https://support.google.com/maps/answer/6230175';
-- The retired RPS /learning/ page is replaced by its current qualifications/course directory.
UPDATE items SET external_url='https://rps.org/qualifications/',revision=revision+1
WHERE title='Royal Photographic Society Learning' AND external_url='https://rps.org/learning/';

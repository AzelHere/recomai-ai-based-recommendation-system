-- Replace catalog links confirmed to return HTTP 404 with their publishers' verified landing pages.
UPDATE items
SET title = CASE external_url
        WHEN 'https://www.cdc.gov/physical-activity/basics/index.html' THEN 'Harvard Health: Exercise and Fitness'
        ELSE title
    END,
    source_name = CASE external_url
        WHEN 'https://www.cdc.gov/physical-activity/basics/index.html' THEN 'Harvard Health Publishing'
        ELSE source_name
    END,
    external_url = CASE external_url
    WHEN 'https://www.strategyzer.com/library/the-lean-startup' THEN 'https://www.strategyzer.com/'
    WHEN 'https://www.adobe.com/creativecloud/photography/discover/photography-basics.html' THEN 'https://www.adobe.com/'
    WHEN 'https://www.ideou.com/pages/creative-confidence' THEN 'https://www.ideou.com/'
    WHEN 'https://learn.microsoft.com/en-us/training/paths/azure-fundamentals-describe-cloud-concepts/' THEN 'https://learn.microsoft.com/'
    WHEN 'https://www.coursera.org/specializations/wharton-entrepreneurship' THEN 'https://www.coursera.org/'
    WHEN 'https://www.consumerfinance.gov/consumer-tools/budgeting/' THEN 'https://www.consumerfinance.gov/consumer-tools/'
    WHEN 'https://www.consumerfinance.gov/consumer-tools/saving/' THEN 'https://www.consumerfinance.gov/'
    WHEN 'https://www.finra.org/investors/insights/asset-allocation-and-diversification' THEN 'https://www.finra.org/investors/'
    WHEN 'https://www.investor.gov/introduction-investing/investing-basics' THEN 'https://www.investor.gov/'
    WHEN 'https://mailchimp.com/resources/marketing-glossary/' THEN 'https://mailchimp.com/'
    WHEN 'https://www.coursera.org/learn/english-for-career-development' THEN 'https://www.coursera.org/learn'
    WHEN 'https://www.cdc.gov/physical-activity/basics/index.html' THEN 'https://www.health.harvard.edu/topics/exercise-and-fitness'
    WHEN 'https://mhanational.org/mental-health-screening-tools/' THEN 'https://mhanational.org/'
    ELSE external_url
END
WHERE external_url IN (
    'https://www.strategyzer.com/library/the-lean-startup',
    'https://www.adobe.com/creativecloud/photography/discover/photography-basics.html',
    'https://www.ideou.com/pages/creative-confidence',
    'https://learn.microsoft.com/en-us/training/paths/azure-fundamentals-describe-cloud-concepts/',
    'https://www.coursera.org/specializations/wharton-entrepreneurship',
    'https://www.consumerfinance.gov/consumer-tools/budgeting/',
    'https://www.consumerfinance.gov/consumer-tools/saving/',
    'https://www.finra.org/investors/insights/asset-allocation-and-diversification',
    'https://www.investor.gov/introduction-investing/investing-basics',
    'https://mailchimp.com/resources/marketing-glossary/',
    'https://www.coursera.org/learn/english-for-career-development',
    'https://www.cdc.gov/physical-activity/basics/index.html',
    'https://mhanational.org/mental-health-screening-tools/'
);

-- Replace the "Demo Life Insurer" test company with a real company directory.
-- Run as postgres in the Supabase SQL Editor (the connector auto-declines
-- data-changing statements like this one as a production write).
--
-- What it does, in order (required because of two guard triggers):
--   1. Inserts 11 real, IRDAI-registered insurance companies (LIC + 10
--      private life/health insurers). Only names + a standard disclaimer are
--      stored; no logos are added (CLAUDE.md branding rule still applies).
--   2. Re-creates the 6 existing demo offers under LIC, because
--      `offers_guard` forbids moving an offer to a different company_id on
--      UPDATE (`invalid_company`). The content is unchanged (same disguised
--      public names, per DECISIONS #77); only the owning company changes
--      from the fake demo-life row to the real LIC row.
--   3. Moves the 4 existing test memberships from demo-life to LIC via the
--      normal withdraw -> request -> approve state machine, because
--      `memberships_guard` forbids changing company_id directly on an
--      already-linked row (`invalid_transition`).
--   4. Deletes the now-unreferenced demo-life offers, then deactivates the
--      demo-life company row (companies are never hard-deleted — OFFERS.md:
--      "Inactive companies disappear from pickers and from sites; never
--      hard-deleted"). It also can't be hard-deleted in practice: withdrawn
--      company_memberships rows keep their company_id as history, so
--      ON DELETE RESTRICT blocks a real DELETE even after every active row
--      has moved off it (confirmed by trying it: 23503 on company_memberships_company_id_fkey).
--
-- Verified safe against the two live demo sites: no site's draft_content or
-- published_content stores any of the 6 old offer ids (offers resolve live
-- by company + status, not by a pinned id), so recreating them is safe.

begin;

insert into public.companies (slug, name, disclaimer, sort) values
('lic', '{"en":"Life Insurance Corporation of India","hi":"भारतीय जीवन बीमा निगम"}'::jsonb,
  '{"en":"Insurance is the subject matter of solicitation. Benefits depend on the policy terms and conditions. Please read the sales brochure carefully before buying.","hi":"बीमा आग्रह की विषय-वस्तु है। लाभ पॉलिसी के नियमों और शर्तों पर निर्भर करते हैं। खरीदने से पहले बिक्री विवरणिका ध्यान से पढ़ें।"}'::jsonb, 10),
('hdfc-life', '{"en":"HDFC Life Insurance Company","hi":"एचडीएफसी लाइफ इंश्योरेंस कंपनी"}'::jsonb,
  '{"en":"Insurance is the subject matter of solicitation. Benefits depend on the policy terms and conditions. Please read the sales brochure carefully before buying.","hi":"बीमा आग्रह की विषय-वस्तु है। लाभ पॉलिसी के नियमों और शर्तों पर निर्भर करते हैं। खरीदने से पहले बिक्री विवरणिका ध्यान से पढ़ें।"}'::jsonb, 20),
('icici-prudential-life', '{"en":"ICICI Prudential Life Insurance","hi":"आईसीआईसीआई प्रूडेंशियल लाइफ इंश्योरेंस"}'::jsonb,
  '{"en":"Insurance is the subject matter of solicitation. Benefits depend on the policy terms and conditions. Please read the sales brochure carefully before buying.","hi":"बीमा आग्रह की विषय-वस्तु है। लाभ पॉलिसी के नियमों और शर्तों पर निर्भर करते हैं। खरीदने से पहले बिक्री विवरणिका ध्यान से पढ़ें।"}'::jsonb, 30),
('sbi-life', '{"en":"SBI Life Insurance Company","hi":"एसबीआई लाइफ इंश्योरेंस कंपनी"}'::jsonb,
  '{"en":"Insurance is the subject matter of solicitation. Benefits depend on the policy terms and conditions. Please read the sales brochure carefully before buying.","hi":"बीमा आग्रह की विषय-वस्तु है। लाभ पॉलिसी के नियमों और शर्तों पर निर्भर करते हैं। खरीदने से पहले बिक्री विवरणिका ध्यान से पढ़ें।"}'::jsonb, 40),
('max-life', '{"en":"Max Life Insurance Company","hi":"मैक्स लाइफ इंश्योरेंस कंपनी"}'::jsonb,
  '{"en":"Insurance is the subject matter of solicitation. Benefits depend on the policy terms and conditions. Please read the sales brochure carefully before buying.","hi":"बीमा आग्रह की विषय-वस्तु है। लाभ पॉलिसी के नियमों और शर्तों पर निर्भर करते हैं। खरीदने से पहले बिक्री विवरणिका ध्यान से पढ़ें।"}'::jsonb, 50),
('bajaj-allianz-life', '{"en":"Bajaj Allianz Life Insurance Company","hi":"बजाज आलियांज लाइफ इंश्योरेंस कंपनी"}'::jsonb,
  '{"en":"Insurance is the subject matter of solicitation. Benefits depend on the policy terms and conditions. Please read the sales brochure carefully before buying.","hi":"बीमा आग्रह की विषय-वस्तु है। लाभ पॉलिसी के नियमों और शर्तों पर निर्भर करते हैं। खरीदने से पहले बिक्री विवरणिका ध्यान से पढ़ें।"}'::jsonb, 60),
('tata-aia-life', '{"en":"Tata AIA Life Insurance Company","hi":"टाटा एआईए लाइफ इंश्योरेंस कंपनी"}'::jsonb,
  '{"en":"Insurance is the subject matter of solicitation. Benefits depend on the policy terms and conditions. Please read the sales brochure carefully before buying.","hi":"बीमा आग्रह की विषय-वस्तु है। लाभ पॉलिसी के नियमों और शर्तों पर निर्भर करते हैं। खरीदने से पहले बिक्री विवरणिका ध्यान से पढ़ें।"}'::jsonb, 70),
('kotak-mahindra-life', '{"en":"Kotak Mahindra Life Insurance Company","hi":"कोटक महिंद्रा लाइफ इंश्योरेंस कंपनी"}'::jsonb,
  '{"en":"Insurance is the subject matter of solicitation. Benefits depend on the policy terms and conditions. Please read the sales brochure carefully before buying.","hi":"बीमा आग्रह की विषय-वस्तु है। लाभ पॉलिसी के नियमों और शर्तों पर निर्भर करते हैं। खरीदने से पहले बिक्री विवरणिका ध्यान से पढ़ें।"}'::jsonb, 80),
('aditya-birla-sun-life', '{"en":"Aditya Birla Sun Life Insurance Company","hi":"आदित्य बिड़ला सन लाइफ इंश्योरेंस कंपनी"}'::jsonb,
  '{"en":"Insurance is the subject matter of solicitation. Benefits depend on the policy terms and conditions. Please read the sales brochure carefully before buying.","hi":"बीमा आग्रह की विषय-वस्तु है। लाभ पॉलिसी के नियमों और शर्तों पर निर्भर करते हैं। खरीदने से पहले बिक्री विवरणिका ध्यान से पढ़ें।"}'::jsonb, 90),
('pnb-metlife', '{"en":"PNB MetLife India Insurance Company","hi":"पीएनबी मेटलाइफ इंडिया इंश्योरेंस कंपनी"}'::jsonb,
  '{"en":"Insurance is the subject matter of solicitation. Benefits depend on the policy terms and conditions. Please read the sales brochure carefully before buying.","hi":"बीमा आग्रह की विषय-वस्तु है। लाभ पॉलिसी के नियमों और शर्तों पर निर्भर करते हैं। खरीदने से पहले बिक्री विवरणिका ध्यान से पढ़ें।"}'::jsonb, 100),
('star-health', '{"en":"Star Health and Allied Insurance Company","hi":"स्टार हेल्थ एंड एलाइड इंश्योरेंस कंपनी"}'::jsonb,
  '{"en":"Insurance is the subject matter of solicitation. Benefits depend on the policy terms and conditions. Please read the sales brochure carefully before buying.","hi":"बीमा आग्रह की विषय-वस्तु है। लाभ पॉलिसी के नियमों और शर्तों पर निर्भर करते हैं। खरीदने से पहले बिक्री विवरणिका ध्यान से पढ़ें।"}'::jsonb, 110);

with lic as (select id from public.companies where slug = 'lic')
insert into public.offers (company_id, content, interest, cta_kind, starts_at, ends_at, sort, status)
select lic.id, x.content, x.interest, 'enquire', x.starts_at, x.ends_at, x.sort, 'draft'
from lic, (values
  ('{"en":{"badge":"New","title":"Lifelong pension, rate locked today","details":"Start from age 18. Pay once or regularly, then choose when income begins (from age 30). Pick income for yourself alone or for you and your spouse. Extra savings during the waiting years can buy more pension.","summary":"Save now and get a guaranteed income for life after you retire. The annuity rate is fixed on the day you start.","disclaimer":"Annuity rates and options are subject to the policy terms."},"hi":{"badge":"नया","title":"आजीवन पेंशन, दर आज ही तय","details":"18 वर्ष की उम्र से शुरू करें। एक बार या नियमित भुगतान करें, फिर चुनें कि आय कब शुरू हो (30 वर्ष से)। आय सिर्फ अपने लिए या पति/पत्नी के साथ संयुक्त रूप से चुनें। इंतज़ार के वर्षों में अतिरिक्त बचत से पेंशन बढ़ाई जा सकती है।","summary":"अभी बचत करें और रिटायरमेंट के बाद जीवन भर गारंटीड आय पाएँ। एन्युटी दर शुरुआत के दिन ही तय हो जाती है।","disclaimer":"एन्युटी दरें और विकल्प पॉलिसी की शर्तों के अधीन हैं।"}}'::jsonb, 'pension', '2026-10-18 18:30:00+00'::timestamptz, '2027-03-31 18:29:59+00'::timestamptz, 1),
  ('{"en":{"badge":"New","title":"Family health cash plan","details":"For adults aged 18 to 65; children from 90 days can be added. The premium stays the same for the full 15 years. Cover grows when you make no claim. If the main insured person dies, future premiums for the family are waived, subject to conditions.","summary":"A fixed daily cash amount in hospital, plus lump sums for major surgery, for you and your children under one plan.","disclaimer":"Benefits are fixed amounts for the listed events, subject to the policy terms."},"hi":{"badge":"नया","title":"पारिवारिक हेल्थ कैश प्लान","details":"18 से 65 वर्ष के वयस्कों के लिए; 90 दिन से बड़े बच्चे जोड़े जा सकते हैं। पूरे 15 वर्ष प्रीमियम एक जैसा रहता है। क्लेम न करने पर कवर बढ़ता है। मुख्य बीमित व्यक्ति के न रहने पर परिवार के आगे के प्रीमियम माफ़, शर्तों के अधीन।","summary":"अस्पताल में हर दिन तय नकद राशि, और बड़ी सर्जरी पर एकमुश्त राशि — आपके और बच्चों के लिए एक ही प्लान में।","disclaimer":"लाभ सूचीबद्ध घटनाओं के लिए तय राशियाँ हैं, पॉलिसी की शर्तों के अधीन।"}}'::jsonb, 'health', '2026-10-18 18:30:00+00'::timestamptz, '2027-03-31 18:29:59+00'::timestamptz, 2),
  ('{"en":{"badge":"New","title":"Cancer care cover","details":"For ages 18 to 65. A quarter of the cover is paid on a listed early-stage diagnosis and the full cover on a listed major stage, with a monthly income for 10 years. Future premiums are waived after an eligible diagnosis. Choose a level or increasing cover.","summary":"Money when you need it most: a payout on an early-stage cancer diagnosis, and the full cover with monthly income at a major stage.","disclaimer":"Payouts apply to the cancer stages listed in the policy, subject to its terms."},"hi":{"badge":"नया","title":"कैंसर केयर कवर","details":"18 से 65 वर्ष के लिए। सूचीबद्ध शुरुआती चरण पर कवर का एक-चौथाई और गंभीर चरण पर पूरा कवर, साथ में 10 वर्ष तक मासिक आय। योग्य निदान के बाद आगे के प्रीमियम माफ़। स्थिर या बढ़ता कवर चुनें।","summary":"जब सबसे ज़्यादा ज़रूरत हो तब पैसा: शुरुआती चरण के कैंसर पर भुगतान, और गंभीर चरण पर पूरा कवर व मासिक आय।","disclaimer":"भुगतान पॉलिसी में सूचीबद्ध कैंसर चरणों पर, उसकी शर्तों के अधीन।"}}'::jsonb, 'health', '2026-10-18 18:30:00+00'::timestamptz, '2027-03-31 18:29:59+00'::timestamptz, 3),
  ('{"en":{"badge":"Limited pay","title":"Savings plan with a booster payout","details":"Pay for 7 to 18 years. Guaranteed additions build up each year you pay. After that you receive a yearly income, and a booster payout five years after the paying term ends. Life cover throughout. Entry from 30 days to 55 years, depending on the term.","summary":"Pay for a few years, earn guaranteed additions every year you pay, then get a regular income and a booster payout later.","disclaimer":"Additions, income and booster amounts depend on the policy terms."},"hi":{"badge":"सीमित भुगतान","title":"बूस्टर भुगतान वाला बचत प्लान","details":"7 से 18 वर्ष तक भुगतान करें। हर भुगतान वर्ष में गारंटीड एडिशन जुड़ते हैं। उसके बाद सालाना आय, और भुगतान अवधि खत्म होने के पाँच वर्ष बाद बूस्टर भुगतान। पूरे समय जीवन सुरक्षा। अवधि के अनुसार 30 दिन से 55 वर्ष तक प्रवेश।","summary":"कुछ ही वर्ष भुगतान करें, हर भुगतान वर्ष में गारंटीड एडिशन पाएँ, फिर नियमित आय और बाद में बूस्टर भुगतान।","disclaimer":"एडिशन, आय और बूस्टर राशि पॉलिसी की शर्तों पर निर्भर हैं।"}}'::jsonb, 'endowment', '2026-09-06 18:30:00+00'::timestamptz, '2027-03-31 18:29:59+00'::timestamptz, 4),
  ('{"en":{"badge":"Women save more","title":"Simple family protection plan","details":"Cover from ₹5 lakh to ₹24 lakh, for ages 18 to 45. Pay once, regularly, or for a limited period. Special lower rates for women. There is no maturity payout: the plan is only about protecting your family.","summary":"Pure life cover at a low cost, so your family gets a fixed, guaranteed amount if something happens to you.","disclaimer":"Pure protection plan with no maturity benefit; subject to the policy terms."},"hi":{"badge":"महिलाओं की बचत","title":"सरल पारिवारिक सुरक्षा प्लान","details":"₹5 लाख से ₹24 लाख तक कवर, 18 से 45 वर्ष के लिए। एक बार, नियमित या सीमित अवधि तक भुगतान करें। महिलाओं के लिए विशेष कम दरें। परिपक्वता पर कोई भुगतान नहीं: यह प्लान सिर्फ परिवार की सुरक्षा के लिए है।","summary":"कम लागत में शुद्ध जीवन सुरक्षा — आपको कुछ होने पर परिवार को तय, गारंटीड राशि मिलती है।","disclaimer":"परिपक्वता लाभ के बिना शुद्ध सुरक्षा प्लान; पॉलिसी की शर्तों के अधीन।"}}'::jsonb, 'term', '2026-09-06 18:30:00+00'::timestamptz, '2027-03-31 18:29:59+00'::timestamptz, 5),
  ('{"en":{"badge":"For two","title":"Savings plan for couples","details":"Pay once, or for 5, 10 or 15 years. Guaranteed additions build up every year. Cover starts from ₹3 lakh. Loans and payouts in instalments are available.","summary":"One plan that covers both of you. If one partner passes away, the remaining premiums are waived and the savings continue.","disclaimer":"Waiver and additions apply as per the policy terms."},"hi":{"badge":"दो के लिए","title":"दंपति के लिए बचत प्लान","details":"एक बार, या 5, 10 या 15 वर्ष तक भुगतान करें। हर वर्ष गारंटीड एडिशन जुड़ते हैं। कवर ₹3 लाख से शुरू। लोन और किस्तों में भुगतान की सुविधा।","summary":"एक ही प्लान में दोनों की सुरक्षा। एक साथी के न रहने पर बाकी प्रीमियम माफ़ और बचत जारी।","disclaimer":"प्रीमियम माफ़ी और एडिशन पॉलिसी की शर्तों के अनुसार।"}}'::jsonb, 'endowment', '2026-05-31 18:30:00+00'::timestamptz, '2027-03-31 18:29:59+00'::timestamptz, 6)
) as x(content, interest, starts_at, ends_at, sort);

update public.offers set status = 'live'
where company_id = (select id from public.companies where slug = 'lic') and status = 'draft';

update public.company_memberships
set status = 'withdrawn'
where company_id = (select id from public.companies where slug = 'demo-life') and status = 'approved';

insert into public.company_memberships (agent_id, company_id, agency_code, insurance_types, status)
select t.agent_id, (select id from public.companies where slug='lic'), t.agency_code, t.insurance_types, 'pending'
from (values
  ('4b71438f-e1fb-4428-a23b-52674bc89862'::uuid, 'Fghk'::text, array['health','motor']::text[]),
  ('a8273883-a2d3-42d0-b13c-52aaffa5856a'::uuid, null::text, array['life']::text[]),
  ('1b5efb09-e8ff-4727-b5db-92bcbf9886df'::uuid, null::text, array['life']::text[]),
  ('c5ffbae9-0302-4770-87c4-7fb5a76c3182'::uuid, null::text, array['life']::text[])
) as t(agent_id, agency_code, insurance_types);

update public.company_memberships
set status = 'approved'
where company_id = (select id from public.companies where slug = 'lic') and status = 'pending';

delete from public.offers where company_id = (select id from public.companies where slug = 'demo-life');
update public.companies set is_active = false, sort = 999 where slug = 'demo-life';

commit;

# Cover recognition

## Input and fallbacks

1. Bundled ML Kit OCR reads the cover locally, retaining line text, relative order and text height.
2. A supported/available local Gemini Nano model first receives an EXIF-corrected, downsampled cover bitmap plus OCR hints. ML Kit Prompt remains pinned to 1.0.0-alpha1, whose published AAR includes ImagePart; its image request API is used without changing the Kotlin toolchain. No cloud AI is called.
3. If image input is unsupported, fails, times out or returns a rejected result, the app tries category-aware text structuring. If that fails, raw OCR lines remain available.
4. Cover text can be viewed/corrected and processed again locally. Title, creator and type are editable in the review before Apply. Nothing is saved until the item form is saved.

Image and text recognition can still be wrong. Text-only inference cannot recover letters that OCR missed without guessing; image input gives the local model additional evidence, not guaranteed recognition. The original cover photograph is not available from a screenshot of a suggestion, so regression fixtures reproduce the reported malformed text and role assignment, not the exact optical conditions of that photograph.

## Validation

Known platform/header text is separated from work-title candidates. Limited fuzzy matching tolerates a damaged brand prefix only when a distinguishing system word and word count remain intact; imperfect matches are labeled as possible platforms. A platform name on its own is rejected as an item title, while titles with additional words such as Nintendo Switch Sports remain candidates. These are conservative heuristics and may reject a genuine work named exactly like a platform; edit the item manually in that case.

Text-only titles must be supported by OCR after case/punctuation normalization. Image-based titles may correct OCR spelling. Creator and supplemental metadata still require supporting cover text; a game creator additionally requires company/credit evidence. Unknown ownership, borrower, condition and hidden attributes are not inferred from a cover. Raw OCR alternatives are labeled as unverified text lines and never assigned an author automatically.

A recognized platform can suggest Game even when the form starts on Book. Image inference may propose another type. Review and adjust the suggested type; applying suggestions preserves existing edits unless replacement is explicitly chosen. Existing saved items and photos are unchanged by recognition.

## Online searches

Identify and find online now opens local recognition/review first. It does not immediately search from the first model response. Find catalog matches uses the reviewed title, creator and supported type. Books/comics use Open Library; CDs/vinyl use MusicBrainz. Games and movies now use Wikidata's public Action API without an account, API key or hosted proxy. Requests send search terms and entity IDs, never the photo, notes or raw cover text. The service also receives ordinary network information such as the device's IP address.

Wikidata lookup searches English labels/aliases, retrieves up to eight candidate entities, resolves their type ancestry (two subclass hops) and related labels, then filters/ranks matches by title, explicit Arabic sequel numbers and any known platform hint. Type ancestry is bounded; deeply specialized types or incomplete records may be missed. Roman/Arabic numeral equivalence is not inferred. This is catalog retrieval, not general web browsing or a guarantee that the match is correct.

Canonical English labels supply title spelling/capitalization. Available publisher, series, earliest catalog publication year and developer/director fill suggestions. A single supported platform or matching recognized platform can be proposed; multiplatform works without a hint leave the platform field blank. Other supported platforms and genre appear in the match detail. Earliest publication year is work-level and may differ from the user's port, region or edition. Language, condition, physical format, region and EAN are not inferred from a work record. Unknown fields stay blank. Catalog facts do not need to appear in the OCR; only the local extraction step requires visual/text support.

Lookup is explicitly initiated, rate-limited to at most one request per 1.1 seconds and bounded to 1 MiB per JSON response, with a 24-hour result cache (up to 100 entries). Cached searches include type, title and platform. Network failure preserves the form. Applying a match fills blanks; an otherwise identical title adopts catalog capitalization. Other existing values are preserved unless Replace existing details is selected. Items are still saved only by the form's Save action. Wikidata source ID and URL are stored with applied metadata. This integration does not fetch game/movie artwork.

## Verification and limits

Core regressions cover malformed Nintendo Switch branding, split game titles, creator assignment, supported/unsupported facts, image-only OCR corrections, platform distinctions and ownership protection. Android tests use bundled OCR and injected local-model adapters for image preference, fallback, rejection and review/correction behavior. They do not run Gemini Nano on the emulator. Actual multimodal availability, accuracy and latency require supported phone/AICore testing. Recognition diagnostics log failure classes without raw model response text.

Primary API references:
- https://developers.google.com/ml-kit/genai/prompt/android/get-started
- https://developers.google.com/ml-kit/genai/prompt/android/prompt-design

- https://www.mediawiki.org/wiki/Wikibase/API
- https://www.wikidata.org/wiki/Help:Data_access

The trimmed Wikidata Mario fixture was retrieved from the public Action API on 2026-10-19 (CC0 data); it retains labels and relevant non-deprecated statement structures. Live service calls are not part of deterministic CI tests.

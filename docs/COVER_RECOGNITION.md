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

Identify and find online now opens local recognition/review first. It does not immediately search from the first model response. Find catalog matches uses the reviewed title, creator and supported type (books/comics or music). It sends those search terms, never the photo or raw cover text. Game metadata still has no online provider until a suitable integration is configured.

## Verification and limits

Core regressions cover malformed Nintendo Switch branding, split game titles, creator assignment, supported/unsupported facts, image-only OCR corrections, platform distinctions and ownership protection. Android tests use bundled OCR and injected local-model adapters for image preference, fallback, rejection and review/correction behavior. They do not run Gemini Nano on the emulator. Actual multimodal availability, accuracy and latency require supported phone/AICore testing. Recognition diagnostics log failure classes without raw model response text.

Primary API references:
- https://developers.google.com/ml-kit/genai/prompt/android/get-started
- https://developers.google.com/ml-kit/genai/prompt/android/prompt-design

# Finding an item

The item editor starts with Type and Find item. Enter a title and choose Find matches; recognition is optional. Search options provides an optional creator and game platform filter. Selecting a result opens the review for the work and the user's copy. Nothing is persisted until the item form's Save action.

## Entry points

- **Title:** search directly without adding a photo or running AI. Correct the query and repeat if the result is absent or wrong. Enter details manually carries the query into the item title without requiring a successful lookup.
- **Photo:** pick/take and edit the image. The optional identification offer reads it locally and opens Review search with an editable title and type. A model/OCR response is never applied as catalog metadata. The raw text is available for reference. Only the reviewed search terms are sent online.
- **Store:** preview a URL directly or through the visible store browser. Review the store's fields and optional image. Find catalog matches next is offered for supported media and selected by default when there is no catalog ID. Edit Title to search before accepting. If an image was selected, editing it finishes first, then the catalog search starts. Canceling the image edit keeps accepted store fields and still permits the search. No redundant OCR offer follows a store/catalog image.
- **Local description:** advanced Local AI options can propose an editable query on supported hardware. Ownership, edition and other model-generated guesses are not applied.

Games/movies use Wikidata, books/comics Open Library, and CDs/vinyl MusicBrainz. Store and catalog import use the same draft; catalog matching keeps storeUrl/dated retail evidence and accepted photographs. Existing catalog fields fill blanks by default. Use catalog title explicitly adopts the reviewed spelling, including replacing a store's product-title suffix. Replace existing details remains an explicit option. Artwork is optional; downloaded catalog artwork passes through the image editor too.

## Your copy versus the work

Choose the platform and edition of a game in review. The known platform can filter a search; a multiplatform result without a hint leaves the copy platform unselected. The result's available platforms remain visible for checking. This is a user-confirmed match, not an assertion that a store product and Wikidata work are identical.

The work's first release year is retained as attributes.firstReleaseYear and labeled First release of the work on the detail screen. It does not fill the copy's releaseYear unless Use first release year for my copy is explicitly selected. Existing copy years are preserved unless replacement is chosen. No port/region-specific year or physical edition is inferred. The existing catalog schema already supports these string attributes; no migration is required.

Product-title cleanup only removes a separated suffix that exactly names a known platform. It keeps sequel numbers, subtitles and edition words. A store title may still need manual correction. Queries remain editable; no fuzzy match auto-applies a result.

## Failure and testing

Requests expose progress and cancellation. Failed/empty searches keep the draft and query available. Canceling an in-flight lookup cannot apply late results. Store throttling/browser restrictions retain their existing handling. Choosing a result, applying its details, and saving the item are separate actions.

Core tests cover conservative query cleanup and copy/year safety. Android fixtures exercise direct title search, canonical capitalization, existing photos/retail evidence, locally recognized query correction before any network call, combined store → photo editor → catalog review, no matches and cancellation. Tests inject catalog/store responses; they do not prove availability or correctness of real store/catalog records or real Gemini Nano recognition.

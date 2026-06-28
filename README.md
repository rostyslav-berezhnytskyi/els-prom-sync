# ELS Prom Sync

ELS Prom Sync is a Spring Boot based automation service that synchronizes a dealer product catalog from Google Sheets, enriches product cards with AI-generated SEO content, calculates marketplace-ready prices, generates a Prom.ua-compatible YML feed, and stores synchronization history in PostgreSQL.

The project was created for a real energy equipment business workflow, where product data is maintained by a supplier in Google Sheets and needs to be transformed into a clean marketplace catalog with correct pricing, availability, images, descriptions, technical specifications, and reporting.

## What the project does

ELS Prom Sync automates the full product feed pipeline:

1. Reads product data from selected Google Sheets tabs.
2. Filters hidden rows, empty rows, headers, service rows, and invalid items.
3. Normalizes dealer categories into internal product categories.
4. Calculates internal sale prices in UAH using USD exchange rate and configurable markup rules.
5. Applies Prom.ua commission gross-up and final rounding.
6. Normalizes product availability into a strict enum.
7. Uses OpenAI to generate SEO product names, descriptions, keywords, vendor names, and technical specifications.
8. Synchronizes product image URLs from local/server folders.
9. Generates a Prom.ua YML XML feed.
10. Validates the generated XML before replacing the production feed file.
11. Sends a Telegram synchronization report.
12. Stores synchronization history in PostgreSQL.
13. Can run as a one-shot Docker job scheduled by cron.

## Main features

### Google Sheets import

The service reads product rows from configured dealer spreadsheet tabs, including:

* solar panels
* hybrid inverters
* grid inverters
* autonomous inverters
* LV batteries
* HV batteries
* solar cable
* BESS systems

It supports:

* allowed tab filtering
* hidden row detection
* custom row range
* different column mappings for different tabs
* retry logic for temporary Google API failures
* missing product detection with a safety threshold

### Product normalization

Dealer data is normalized before being saved to the database.

The application keeps the original dealer values, but also stores normalized values used by the feed logic.

Examples:

* raw availability: `в наявності`, `В наявності`, `в дорозі`, `резерв`, `-`
* normalized availability enum:

    * `IN_STOCK`
    * `ON_ORDER`
    * `RESERVED`
    * `OUT_OF_STOCK`
    * `UNKNOWN`

This prevents unexpected dealer text from accidentally marking a product as available.

### Price calculation

The project has two separate price calculation layers.

Internal price calculation:

* dealer price in USD
* Monobank USD/UAH exchange rate with fallback rate
* category-specific markup
* floating markup depending on product cost
* special logic for solar panels where dealer price is USD/W
* special logic for solar cable

Prom.ua price calculation:

* configurable Prom commission percentage by category
* tiered commission logic
* configurable commission threshold
* configurable reduced commission above threshold
* final rounding step in UAH

### AI product enrichment

For new products, or for products with missing AI data, the service can call OpenAI to generate:

* Ukrainian SEO product name
* Russian SEO product name
* Ukrainian HTML description
* Russian HTML description
* Ukrainian keywords
* Russian keywords
* vendor name
* technical specifications as JSON

The prompt logic is category-aware and uses different schemas/rules for:

* inverters
* batteries
* solar panels
* solar cable
* BESS
* BMS modules
* battery racks
* smart meters
* current transformers
* BESS accessories

AI execution can be enabled or disabled through environment variables.

### Image synchronization

Product photos are stored in local/server folders and mapped into public URLs.

The service:

* builds safe folder names from SKU
* normalizes visually similar Cyrillic characters in SKU names
* scans category/SKU folders
* limits the number of images per product
* saves generated image URLs into PostgreSQL
* uses stored image URLs during YML generation

Example media structure:

```text
products/
  hybrid-inverters/
    SUN-8K-SG05LP1-EU-AM2-P/
      1-main.webp
      2-side.webp
  battery-hv/
    BOS-B-Pack16-A3/
      1-main.webp
      datasheet.png
```

### Prom.ua YML feed generation

The generated XML includes:

* product ID / SKU
* vendorCode
* price
* currency
* categoryId
* image URLs
* name / name_ua
* description / description_ua
* keywords / keywords_ua
* vendor
* technical parameters
* availability
* in_stock flag
* pickup/delivery rules
* sales_notes
* warranty parameter

The feed generator uses safe XML escaping and CDATA handling for descriptions.

### XML validation before file replacement

Before the production `prom.xml` file is replaced, the generated XML is validated.

Validation checks include:

* valid XML structure
* correct root tag
* at least one category
* at least one offer
* non-empty offer ID
* duplicate offer IDs
* non-empty name/name_ua
* non-empty categoryId
* valid positive price
* max 10 pictures per offer
* non-empty picture URLs

If validation fails, the old `prom.xml` is kept unchanged.

The file is written atomically through a temporary file replacement strategy.

### Telegram reporting

After each sync run, the service sends a Telegram report with:

* Google Sheets status
* YML feed status
* number of products in the feed
* processed rows
* skipped rows
* new products
* price changes
* Prom price changes
* availability changes
* name changes
* missing products
* products without photos
* errors

The report is split into several Telegram messages if it exceeds Telegram message length limits.

### Sync history

Each synchronization run is stored in PostgreSQL.

The history table stores:

* start time
* finish time
* duration
* status
* processed rows
* skipped rows
* new products
* price changes
* availability changes
* missing images
* error count
* error summary
* changes summary

This makes it possible to understand when the last successful sync happened and what changed during each run.

### Production logging

The application writes logs to file with rolling policy support.

Logs are used for technical diagnostics, while Telegram reports are used for business-level monitoring.

## Tech stack

* Java 21
* Spring Boot
* Spring Data JPA
* PostgreSQL
* Hibernate
* Docker
* Docker Compose
* Google Sheets API
* OpenAI API
* Telegram Bot API
* Monobank currency API
* XML/YML feed generation

## Architecture overview

```text
Google Sheets
     ↓
GoogleSheetsService
     ↓
ProductSyncService
     ↓
PostgreSQL products table
     ↓
ProductMediaSyncService
     ↓
PromFeedController
     ↓
FeedValidationService
     ↓
prom.xml
     ↓
nginx / Cloudflare / Prom.ua
```

Reporting and observability:

```text
SyncReport
   ├── TelegramNotificationService
   ├── SyncHistoryService
   └── application log file
```

## Running locally

### Requirements

* Java 21
* Maven
* Docker
* PostgreSQL or Docker Compose
* Google OAuth credentials
* OpenAI API key
* Telegram bot token, optional

### Start PostgreSQL

```bash
docker compose up -d postgres
```

### Configure environment variables

Create `.env` in the project root.

Example:

```env
DB_HOST=localhost
DB_PORT=5433
DB_NAME=els_prom_sync
DB_USERNAME=els_admin
DB_PASSWORD=change_me

DEALER_SPREADSHEET_ID=your_google_sheet_id
GOOGLE_CREDENTIALS_FILE=credentials.json
GOOGLE_TOKENS_DIR=tokens

OPENAI_API_KEY=your_openai_api_key
APP_AI_ENABLED=true
APP_AI_BACKFILL_MISSING=false

APP_RUN_ONCE=false
APP_DETECT_MISSING_PRODUCTS=false

MEDIA_PRODUCTS_DIR=D:/els-media/products
MEDIA_PUBLIC_BASE_URL=https://media.example.com/images/products
FEED_OUTPUT_FILE=D:/els-media/feed/prom.xml

TELEGRAM_ENABLED=false
TELEGRAM_BOT_TOKEN=
TELEGRAM_CHAT_ID=
```

### Run the application

```bash
mvn spring-boot:run
```

The feed endpoint is available at:

```text
/api/feed/prom.xml
```

## Production deployment

The production deployment is designed as a one-shot Docker job.

The app starts, performs synchronization, writes the feed file, sends a report, saves sync history, and exits.

Recommended production mode:

```env
APP_RUN_ONCE=true
APP_AI_ENABLED=false
APP_AI_BACKFILL_MISSING=false
APP_DETECT_MISSING_PRODUCTS=true
MEDIA_CREATE_PRODUCT_FOLDERS=false
LOG_FILE=/app/logs/els-prom-sync.log
```

### Docker Compose

The server uses PostgreSQL as a long-running container and the application as a one-shot container.

Example:

```bash
docker compose -f compose.server.yml up -d postgres
docker compose -f compose.server.yml run --rm app
```

### Cron scheduling

The recommended scheduler is external cron, not an internal Java scheduler.

Example daily run at 21:00:

```cron
0 21 * * * /home/admin_els/els-prom-sync/scripts/run-prom-sync.sh
```

This approach keeps the Java application simple and reliable:

```text
cron → docker compose run --rm app → generate prom.xml → exit
```

## Security notes

The following files must not be committed to Git:

```text
.env
credentials.json
tokens/
logs/
secrets/
```

All secrets are configured through environment variables or mounted files.

Google OAuth credentials and tokens are mounted into the container at runtime.

## Project status

Implemented:

* Google Sheets import
* PostgreSQL persistence
* category normalization
* availability enum
* price calculation
* Prom.ua commission calculation
* AI content generation
* image URL synchronization
* safe XML generation
* XML validation
* atomic feed file write
* Telegram reporting
* sync history
* Docker deployment
* production logging
* retry handling for Google Sheets and OpenAI

Planned improvements:

* admin UI for sync history
* tests for pricing and XML validation
* configurable spreadsheet row ranges
* more detailed AI validation
* product-level change history
* manual product override support
* multi-dealer support

## Why this project is useful

This project solves a real business automation problem: converting semi-structured supplier data into a production-ready marketplace feed.

It demonstrates:

* integration with third-party APIs
* data normalization
* database persistence
* AI-assisted content generation
* XML feed generation
* safe file generation
* retry/error handling
* Docker deployment
* production monitoring
* scheduled background jobs
* practical business logic implementation in Java/Spring Boot

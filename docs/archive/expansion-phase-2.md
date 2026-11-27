# Phase 2: Database-Backed Search Engine Expansion

## Current Status ✅

The project now has a **real persistence layer** ready for growth beyond in-memory sample data.

### Two Deployment Paths

#### Path 1: Local Development (Default)
```text
H2 In-Memory Database + Sample Documents
```

**Features:**
- Zero dependencies beyond Java/Maven
- Instant startup
- Sample documents seeded on launch
- Perfect for learning and testing

**To run:**
```powershell
.\tools\apache-maven-3.9.16\bin\mvn spring-boot:run
# Backend on localhost:8080
# Frontend on localhost:5173 (run npm run dev in frontend/ folder)
```

#### Path 2: Production-Ready (With Docker)
```text
PostgreSQL + Redis + Real Documents
```

**Features:**
- Persistent database storage
- Optional Redis caching
- Scales to real search data
- Environment-based configuration

**To run:**
```powershell
# Start services (requires Docker)
.\scripts\start-local-services.ps1

# Run app with postgres profile
.\tools\apache-maven-3.9.16\bin\mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=postgres"
```

**Environment variables (all optional, show defaults):**
```text
DB_URL=jdbc:postgresql://localhost:5432/searchengine
DB_USERNAME=searchengine
DB_PASSWORD=your_local_password
CACHE_TYPE=simple
```

## What Enables Broader Search

The persistence layer unblocks the next phase: **crawler expansion**.

### Current Bottleneck
Right now, search base is limited to 3 sample documents hardcoded in:
```java
SearchEngineService.seedSampleDocuments()
```

This is intentional for Phase 1 (learning). Phase 2 changes this.

### Next Phase: Document Ingestion

**Goal:** Move from 3 static documents → dynamic document corpus

**Options to implement:**
1. **File crawler:** Read `.txt` or `.md` files from disk
2. **Web crawler:** Fetch pages from a list of URLs
3. **API ingestion:** Accept documents via `/api/documents/batch`
4. **Database seeding:** Load initial corpus from CSV/JSON on startup

## Architecture Readiness

The JPA layer is already in place:

```text
DocumentEntity (JPA model)
  ↓
DocumentRepository (Spring Data JPA)
  ↓
SearchEngineService (in-memory index + persistence)
```

This means:
- ✅ Documents can be persisted
- ✅ Documents can be queried from DB on startup
- ✅ New documents can be added via API
- ✅ Search index loads from the database

## Immediate Next Steps

1. **Verify end-to-end flow** (done locally via browser)
2. **Document both paths clearly** (this file)
3. **Implement first document source:**
   - Option: Simple file-based corpus loader
   - Option: Batch document API endpoint
   - Option: CSV seeder on startup
4. **Expand test coverage** for new ingestion path

## Technical Debt

- `SearchEngineService` still calls `seedSampleDocuments()` unconditionally
  - Should check DB first, then seed only if empty
  - Or: extract to a separate optional initializer
- Frontend has hardcoded query parameter validation
  - Could add autocomplete or query suggestions
- No rate limiting on API endpoints
  - Important before opening to real crawlers

## Success Criteria

Phase 2 complete when:
- ✅ Persistence layer active and tested
- ⏳ At least one real document corpus ingested (>100 docs)
- ⏳ Search works across database-backed documents
- ⏳ Ranking quality validated on real corpus
- ⏳ Both deployment paths documented


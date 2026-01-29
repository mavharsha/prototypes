# Understanding Confluence: Storage and Search Architecture

This document explains how Atlassian Confluence stores pages and implements search functionality.

## Table of Contents

1. [Overview](#overview)
2. [Page Storage Architecture](#page-storage-architecture)
3. [Database Schema](#database-schema)
4. [The Storage Format (XHTML)](#the-storage-format-xhtml)
5. [Search and Indexing with Lucene](#search-and-indexing-with-lucene)
6. [Index Fields](#index-fields)
7. [How It All Works Together](#how-it-all-works-together)

---

## Overview

Confluence is a wiki-based collaboration platform that stores content in two primary locations:

| Data Type | Storage Location |
|-----------|------------------|
| Page content, metadata, users, spaces | **Database** (PostgreSQL, MySQL, Oracle, SQL Server) |
| Attachments, plugins, config files | **File System** (Confluence Home Directory) |
| Search indexes | **File System** (`<confluence-home>/index`) |

---

## Page Storage Architecture

### The Two-Table Model

Confluence separates page metadata from page content:

```
┌─────────────────────────────────────────────────────────────────┐
│                         SPACES                                   │
│  (SPACEID, SPACEKEY, SPACENAME, DESCRIPTION, SPACETYPE...)     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ 1:N
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                         CONTENT                                  │
│  Page metadata: ID, Title, Creator, Created, Modified, Version │
│  (CONTENTID, SPACEID, TITLE, CREATOR, CREATIONDATE, VERSION...) │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ 1:1
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       BODYCONTENT                                │
│  Actual page content in XHTML storage format                     │
│  (BODYCONTENTID, CONTENTID, BODY, BODYTYPE)                     │
└─────────────────────────────────────────────────────────────────┘
```

### Why This Separation?

1. **Performance**: Metadata queries don't load full page content
2. **Versioning**: Each version can have different body content
3. **Storage Efficiency**: Body content can be compressed separately
4. **Search Optimization**: Metadata indexed differently than content

---

## Database Schema

### Key Tables

#### SPACES Table
```sql
CREATE TABLE SPACES (
    SPACEID         BIGINT PRIMARY KEY,
    SPACEKEY        VARCHAR(255) NOT NULL UNIQUE,  -- e.g., "ENGINEERING"
    SPACENAME       VARCHAR(255),                   -- e.g., "Engineering Team"
    DESCRIPTION     TEXT,
    SPACETYPE       VARCHAR(255),                   -- "global" or "personal"
    HOMEPAGE        BIGINT,                         -- FK to CONTENT
    CREATOR         VARCHAR(255),
    CREATIONDATE    TIMESTAMP
);
```

#### CONTENT Table
```sql
CREATE TABLE CONTENT (
    CONTENTID       BIGINT PRIMARY KEY,
    CONTENTTYPE     VARCHAR(255),      -- 'PAGE', 'BLOGPOST', 'COMMENT', 'ATTACHMENT'
    TITLE           VARCHAR(255),
    LOWERTITLE      VARCHAR(255),      -- For case-insensitive search
    VERSION         INT,
    CREATOR         VARCHAR(255),      -- User key
    CREATIONDATE    TIMESTAMP,
    LASTMODIFIER    VARCHAR(255),
    LASTMODDATE     TIMESTAMP,
    VERSIONCOMMENT  TEXT,
    PREVVER         BIGINT,            -- Previous version (FK to CONTENT)
    CONTENT_STATUS  VARCHAR(255),      -- 'current', 'draft', 'deleted'
    SPACEID         BIGINT,            -- FK to SPACES
    PARENTID        BIGINT,            -- FK to CONTENT (parent page)
    MESSAGEID       VARCHAR(255),      -- For comments
    DRAFTPAGEID     BIGINT,
    DRAFTSPACEKEY   VARCHAR(255),
    DRAFTTYPE       VARCHAR(255),
    PLUGINKEY       VARCHAR(255),      -- For plugin-generated content
    PLUGINVERSION   VARCHAR(255)
);
```

#### BODYCONTENT Table
```sql
CREATE TABLE BODYCONTENT (
    BODYCONTENTID   BIGINT PRIMARY KEY,
    CONTENTID       BIGINT NOT NULL,   -- FK to CONTENT
    BODY            TEXT,              -- The actual XHTML content
    BODYTYPE        INT                -- 0=wiki markup, 2=XHTML storage format
);
```

#### Other Important Tables

| Table | Purpose |
|-------|---------|
| `CONTENT_LABEL` | Labels/tags attached to pages |
| `LABEL` | Label definitions |
| `ATTACHMENTS` | Attachment metadata |
| `USER_MAPPING` | User identity mapping |
| `CONTENT_PERM` | Page-level permissions |
| `PAGETEMPLATES` | Page template definitions |
| `CONFVERSION` | Version history tracking |

---

## The Storage Format (XHTML)

Confluence stores page content in an XHTML-based format (technically XML with custom elements).

### Basic Structure

```xml
<?xml version="1.0" encoding="UTF-8"?>
<ac:confluence xmlns:ac="http://www.atlassian.com/schema/confluence/4/ac/"
               xmlns:ri="http://www.atlassian.com/schema/confluence/4/ri/">

  <!-- Page content starts here -->
  <p>This is a paragraph of text.</p>

  <h1>Heading Level 1</h1>

  <p>More content with <strong>bold</strong> and <em>italic</em> text.</p>

</ac:confluence>
```

### Text Formatting

```xml
<!-- Basic formatting -->
<p>Regular paragraph text</p>
<p><strong>Bold text</strong></p>
<p><em>Italic text</em></p>
<p><u>Underlined text</u></p>
<p><s>Strikethrough text</s></p>
<p><code>Inline code</code></p>
<p>Text with <sub>subscript</sub> and <sup>superscript</sup></p>

<!-- Headings -->
<h1>Heading 1</h1>
<h2>Heading 2</h2>
<h3>Heading 3</h3>
```

### Lists

```xml
<!-- Unordered list -->
<ul>
  <li>Item 1</li>
  <li>Item 2
    <ul>
      <li>Nested item</li>
    </ul>
  </li>
</ul>

<!-- Ordered list -->
<ol>
  <li>First item</li>
  <li>Second item</li>
</ol>

<!-- Task list -->
<ac:task-list>
  <ac:task>
    <ac:task-id>1</ac:task-id>
    <ac:task-status>incomplete</ac:task-status>
    <ac:task-body>Todo item text</ac:task-body>
  </ac:task>
  <ac:task>
    <ac:task-id>2</ac:task-id>
    <ac:task-status>complete</ac:task-status>
    <ac:task-body>Completed item</ac:task-body>
  </ac:task>
</ac:task-list>
```

### Tables

```xml
<table class="confluenceTable">
  <tbody>
    <tr>
      <th class="confluenceTh">Header 1</th>
      <th class="confluenceTh">Header 2</th>
      <th class="confluenceTh">Header 3</th>
    </tr>
    <tr>
      <td class="confluenceTd">Row 1, Cell 1</td>
      <td class="confluenceTd">Row 1, Cell 2</td>
      <td class="confluenceTd">Row 1, Cell 3</td>
    </tr>
    <tr>
      <td class="confluenceTd">Row 2, Cell 1</td>
      <td class="confluenceTd">Row 2, Cell 2</td>
      <td class="confluenceTd">Row 2, Cell 3</td>
    </tr>
  </tbody>
</table>
```

### Links

```xml
<!-- Internal page link -->
<ac:link>
  <ri:page ri:space-key="ENGINEERING" ri:content-title="Getting Started"/>
  <ac:link-body>Click here for setup guide</ac:link-body>
</ac:link>

<!-- Link to specific anchor -->
<ac:link ac:anchor="section-name">
  <ri:page ri:content-title="Documentation"/>
  <ac:link-body>Jump to section</ac:link-body>
</ac:link>

<!-- External link -->
<a href="https://example.com">External Link</a>

<!-- Attachment link -->
<ac:link>
  <ri:attachment ri:filename="document.pdf"/>
  <ac:link-body>Download PDF</ac:link-body>
</ac:link>
```

### Images and Attachments

```xml
<!-- Embedded image from attachment -->
<ac:image ac:align="center" ac:width="500">
  <ri:attachment ri:filename="diagram.png"/>
</ac:image>

<!-- Image from URL -->
<ac:image>
  <ri:url ri:value="https://example.com/image.png"/>
</ac:image>

<!-- Image with effects -->
<ac:image ac:border="true" ac:thumbnail="true">
  <ri:attachment ri:filename="screenshot.png"/>
</ac:image>
```

### Macros

Macros are Confluence's extension mechanism:

```xml
<!-- Code block macro -->
<ac:structured-macro ac:name="code" ac:schema-version="1">
  <ac:parameter ac:name="language">java</ac:parameter>
  <ac:parameter ac:name="title">Example Code</ac:parameter>
  <ac:parameter ac:name="linenumbers">true</ac:parameter>
  <ac:plain-text-body><![CDATA[
public class HelloWorld {
    public static void main(String[] args) {
        System.out.println("Hello, World!");
    }
}
  ]]></ac:plain-text-body>
</ac:structured-macro>

<!-- Info panel macro -->
<ac:structured-macro ac:name="info" ac:schema-version="1">
  <ac:rich-text-body>
    <p>This is an informational message.</p>
  </ac:rich-text-body>
</ac:structured-macro>

<!-- Warning panel -->
<ac:structured-macro ac:name="warning" ac:schema-version="1">
  <ac:parameter ac:name="title">Important!</ac:parameter>
  <ac:rich-text-body>
    <p>Be careful with this operation.</p>
  </ac:rich-text-body>
</ac:structured-macro>

<!-- Table of contents -->
<ac:structured-macro ac:name="toc" ac:schema-version="1">
  <ac:parameter ac:name="maxLevel">3</ac:parameter>
  <ac:parameter ac:name="minLevel">1</ac:parameter>
</ac:structured-macro>

<!-- Include another page -->
<ac:structured-macro ac:name="include" ac:schema-version="1">
  <ac:parameter ac:name="0">
    <ac:link>
      <ri:page ri:space-key="DOCS" ri:content-title="Shared Content"/>
    </ac:link>
  </ac:parameter>
</ac:structured-macro>

<!-- Jira issues macro -->
<ac:structured-macro ac:name="jira" ac:schema-version="1">
  <ac:parameter ac:name="server">JIRA Server</ac:parameter>
  <ac:parameter ac:name="jqlQuery">project = PROJ AND status = Open</ac:parameter>
</ac:structured-macro>
```

### User Mentions and Dates

```xml
<!-- User mention -->
<ac:link>
  <ri:user ri:userkey="user-key-12345"/>
</ac:link>

<!-- Date -->
<time datetime="2024-03-15"/>
```

### Page Layouts

```xml
<!-- Two-column layout -->
<ac:layout>
  <ac:layout-section ac:type="two_equal">
    <ac:layout-cell>
      <p>Left column content</p>
    </ac:layout-cell>
    <ac:layout-cell>
      <p>Right column content</p>
    </ac:layout-cell>
  </ac:layout-section>
</ac:layout>

<!-- Three-column layout -->
<ac:layout>
  <ac:layout-section ac:type="three_equal">
    <ac:layout-cell><p>Column 1</p></ac:layout-cell>
    <ac:layout-cell><p>Column 2</p></ac:layout-cell>
    <ac:layout-cell><p>Column 3</p></ac:layout-cell>
  </ac:layout-section>
</ac:layout>
```

---

## Search and Indexing with Lucene

### Lucene Integration Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                        Confluence Application                         │
└──────────────────────────────────────────────────────────────────────┘
                                   │
                    ┌──────────────┴──────────────┐
                    │                             │
                    ▼                             ▼
         ┌──────────────────┐          ┌──────────────────┐
         │   Event Queue    │          │   Search Query   │
         │  (Create/Update) │          │    Processing    │
         └──────────────────┘          └──────────────────┘
                    │                             │
                    │  Batch Processing           │ Query Parsing
                    │  (every ~5 seconds)         │
                    ▼                             ▼
         ┌──────────────────┐          ┌──────────────────┐
         │   Index Writer   │          │ Index Searcher   │
         │  (Lucene)        │◄────────►│  (Lucene)        │
         └──────────────────┘          └──────────────────┘
                    │                             │
                    └──────────────┬──────────────┘
                                   │
                                   ▼
                    ┌──────────────────────────────┐
                    │      Lucene Index Files      │
                    │   <confluence-home>/index    │
                    └──────────────────────────────┘
```

### Index Structure

Confluence maintains two main indexes:

| Index | Location | Purpose |
|-------|----------|---------|
| Content Index | `<home>/index/content` | Full-text search of pages, blogs, comments |
| Change Index | `<home>/index/change` | Recent changes, activity feeds |

### Index Update Flow

1. **User edits a page** → Content saved to database
2. **Event generated** → Added to indexing queue
3. **Batch processor** → Runs every ~5 seconds
4. **Document created** → Page converted to Lucene Document
5. **Index updated** → New segments written to disk
6. **Searcher refreshed** → Near real-time search available

### Cluster Synchronization

In Data Center (clustered) deployments:

```
┌─────────────────────────────────────────────────────────────────┐
│                     Shared Database                              │
│                  (Journal Service Table)                         │
└─────────────────────────────────────────────────────────────────┘
        │                    │                    │
        ▼                    ▼                    ▼
┌───────────────┐   ┌───────────────┐   ┌───────────────┐
│    Node 1     │   │    Node 2     │   │    Node 3     │
│ Local Index   │   │ Local Index   │   │ Local Index   │
│  (Lucene)     │   │  (Lucene)     │   │  (Lucene)     │
└───────────────┘   └───────────────┘   └───────────────┘
```

Each node maintains its own local index copy, synchronized via journal service.

---

## Index Fields

### Content Fields

| Field Name | Type | Description | Searchable |
|------------|------|-------------|------------|
| `contentId` | String | Unique content identifier | Exact match |
| `type` | String | Content type (page, blogpost, comment) | Exact match |
| `title` | TextField | Page/blog title | Full-text |
| `content` | TextField | Body text (extracted from XHTML) | Full-text |
| `spaceKey` | String | Space identifier | Exact match |
| `spaceName` | TextField | Space display name | Full-text |
| `creatorKey` | String | Creator user key | Exact match |
| `creatorName` | TextField | Creator display name | Full-text |
| `lastModifierKey` | String | Last modifier user key | Exact match |
| `created` | Long | Creation timestamp | Range query |
| `modified` | Long | Last modification timestamp | Range query |
| `contentStatus` | String | Status (current, draft) | Exact match |
| `version` | Int | Version number | Range query |

### Label Fields

| Field Name | Type | Description |
|------------|------|-------------|
| `labelText` | TextField | Label names | Full-text |
| `labelId` | String | Label IDs | Exact match |

### Attachment Fields

| Field Name | Type | Description |
|------------|------|-------------|
| `filename` | TextField | Attachment filename | Full-text |
| `fileExtension` | String | File extension | Exact match |
| `fileSize` | Long | File size in bytes | Range query |
| `mediaType` | String | MIME type | Exact match |

### Hierarchical Fields

| Field Name | Type | Description |
|------------|------|-------------|
| `parentId` | String | Parent page ID | Exact match |
| `ancestorIds` | String[] | All ancestor page IDs | Exact match |

### Example Lucene Document

```java
Document doc = new Document();

// Identifiers
doc.add(new StringField("contentId", "12345678", Field.Store.YES));
doc.add(new StringField("type", "page", Field.Store.YES));
doc.add(new StringField("spaceKey", "ENGINEERING", Field.Store.YES));

// Searchable text fields
doc.add(new TextField("title", "Getting Started Guide", Field.Store.YES));
doc.add(new TextField("content", extractedPlainText, Field.Store.NO));
doc.add(new TextField("labelText", "documentation howto onboarding", Field.Store.NO));

// Faceting and filtering
doc.add(new StringField("contentStatus", "current", Field.Store.NO));
doc.add(new StringField("creatorKey", "user-abc-123", Field.Store.NO));

// Date fields for range queries
doc.add(new LongPoint("created", creationTimestamp));
doc.add(new LongPoint("modified", modificationTimestamp));
doc.add(new NumericDocValuesField("modified", modificationTimestamp)); // For sorting

// Hierarchical navigation
doc.add(new StringField("parentId", "12345600", Field.Store.NO));
doc.add(new StringField("ancestorIds", "12345600", Field.Store.NO));
doc.add(new StringField("ancestorIds", "12345000", Field.Store.NO));
```

---

## How It All Works Together

### Creating a Page

```
User clicks "Create" in Confluence
            │
            ▼
┌─────────────────────────────────┐
│  1. Insert into CONTENT table   │
│     - Generate CONTENTID        │
│     - Set metadata (title,      │
│       creator, dates, etc.)     │
└─────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────┐
│  2. Insert into BODYCONTENT     │
│     - Store XHTML content       │
│     - Link to CONTENTID         │
└─────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────┐
│  3. Queue indexing event        │
│     - Add to indexing queue     │
│     - Async processing          │
└─────────────────────────────────┘
            │
            ▼ (async, ~5 seconds)
┌─────────────────────────────────┐
│  4. Create Lucene Document      │
│     - Extract plain text        │
│     - Parse XHTML               │
│     - Build field values        │
└─────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────┐
│  5. Add to Lucene Index         │
│     - Write to segments         │
│     - Commit changes            │
│     - Refresh searcher          │
└─────────────────────────────────┘
```

### Searching

```
User enters search query
            │
            ▼
┌─────────────────────────────────┐
│  1. Parse CQL/Query             │
│     - title:guide AND           │
│       space:ENGINEERING         │
└─────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────┐
│  2. Build Lucene Query          │
│     - BooleanQuery with clauses │
│     - Apply filters             │
│     - Add boosts                │
└─────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────┐
│  3. Execute Search              │
│     - Query Lucene index        │
│     - Collect matching docs     │
│     - Score and rank            │
└─────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────┐
│  4. Apply Permissions           │
│     - Filter results by         │
│       user permissions          │
│     - Space restrictions        │
└─────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────┐
│  5. Load Full Content           │
│     - Fetch from database       │
│     - Generate excerpts         │
│     - Highlight matches         │
└─────────────────────────────────┘
            │
            ▼
       Search Results
```

---

## References

- [Confluence Storage Format](https://confluence.atlassian.com/doc/confluence-storage-format-790796544.html)
- [Confluence Data Model](https://confluence.atlassian.com/doc/confluence-data-model-127369837.html)
- [Confluence Search Fields](https://confluence.atlassian.com/doc/confluence-search-fields-161188.html)
- [Content Index Administration](https://confluence.atlassian.com/doc/content-index-administration-148844.html)
- [Where Does Confluence Store Its Data](https://support.atlassian.com/confluence/kb/where-does-confluence-store-its-data/)

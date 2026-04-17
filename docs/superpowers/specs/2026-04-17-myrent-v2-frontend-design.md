# MyRent v2 Frontend Design

- Date: 2026-04-17
- Project: MyRent
- Scope: v2 frontend redesign, phase 1
- Status: Draft approved in conversation, written for review before implementation planning

## 1. Background

MyRent is currently a first-version rental project with a Java backend and a separate Vue 3 frontend. The backend already covers core rental flows, but the current frontend reads as a training/demo project rather than a product someone would remember after opening it.

The user wants to use this project for job seeking, while also leaving room to deploy it as a real product later. That creates a dual requirement:

- the frontend must make a strong first impression for interviewers
- the frontend must still behave like a believable rental product rather than a pure showcase page

This design therefore targets a v2 frontend that raises visual quality first, while keeping technical choices pragmatic enough to support later backend expansion.

## 2. Goals

### Primary goals

- Upgrade the frontend from "functional demo" to "productized portfolio project"
- Establish a cohesive visual system before adding more backend-driven functionality
- Make the desktop experience strong enough for interview review
- Preserve a path to future online deployment rather than building a one-off mock website

### Non-goals for phase 1

- Completing all backend interfaces
- Rebuilding every business workflow end to end
- Deep chat interaction redesign
- Advanced map interactions
- Full order lifecycle polish
- Filling all placeholder pages under "Mine"

## 3. Product Positioning

The v2 frontend should feel like:

- a real rental product
- with a stronger editorial/design sensibility than a typical CRUD business app
- without becoming a decorative landing page that hides the product

Chosen positioning from discussion:

- Visual direction: `Editorial Urban`
- Overall tone: `design-forward but not flashy`
- Product rhythm: `restrained productization`
- Homepage role: `brand narrative + product entry`
- Device priority: `desktop first`
- Navigation pattern: `top navigation`

This means the product should communicate taste, curation, and urban living quality, but still let users move quickly into search, discovery, listing details, messaging, and later transaction flows.

## 4. Design Principles

### 4.1 First impression before feature density

The current project suffers more from the lack of a system than from missing individual pages. Phase 1 should prioritize the overall impression, visual hierarchy, and consistency before adding more functionality.

### 4.2 Real product, not fake showcase

The homepage may carry stronger branding and editorial presentation, but the site must still quickly transition into real product entry points. Every visual choice should support product trust, not just aesthetics.

### 4.3 Desktop first, mobile compatible

Interviewers are likely to review the project on desktop. The new layout should therefore be intentionally designed for wide screens instead of stretching a mobile H5 layout. Mobile remains supported, but desktop quality is the primary design constraint in phase 1.

### 4.4 Consistency over local optimization

No page should invent its own style. Buttons, cards, spacing, typography, layout width, empty states, and status presentation must come from one shared system.

### 4.5 Upgrade the foundation before page polish

The project should not continue to rely on a handful of generic global CSS utility classes. A proper design foundation must come first so later pages can be built quickly without visual drift.

## 5. Visual System

### 5.1 Visual keywords

- editorial
- urban
- curated
- warm
- restrained
- premium but approachable

### 5.2 Color direction

The current default blue product styling should be replaced by a warmer, more distinctive palette.

Target palette direction:

- background: warm off-white / rice / soft beige
- primary text: deep brown or charcoal
- accent surfaces: muted sand, stone, or soft clay
- interactive emphasis: restrained dark tone or low-saturation accent, not bright SaaS blue
- status colors: still readable and standard, but harmonized into the palette

### 5.3 Typography direction

Typography should carry part of the premium feel. The interface should not rely on generic "white page + medium blue button" aesthetics.

Rules:

- stronger hero and section headings
- calmer body text
- more intentional spacing between heading, support text, and CTA
- typography should create hierarchy before borders and shadows do

### 5.4 Surfaces and spacing

Rules:

- use fewer but better card styles
- prefer larger radii and softer elevation for key surfaces
- keep grid and spacing consistent across homepage, list, and detail pages
- avoid dense stacks of visually identical cards

## 6. Information Architecture

### 6.1 Homepage role

The homepage is not just a list screen anymore. It should have two responsibilities:

- establish product identity in the first screen
- transition users into search and discovery immediately after

### 6.2 Approved homepage structure

Approved structure from discussion:

1. Top navigation
2. Hero section with split responsibilities
   - left: narrative/brand statement
   - right: search dock and quick entry actions
3. Brand content strip
   - editorial or lifestyle-flavored content blocks
   - enough to strengthen identity without becoming a content site
4. Featured listings and supporting product cards
5. Footer credibility area

This structure is intentionally neither a pure landing page nor a pure search dashboard.

### 6.3 Homepage content priority

Under the search dock, the user selected brand-content-first exposure rather than immediately surfacing transaction or interaction modules.

This means the homepage should highlight:

- curated topics
- neighborhood/lifestyle framing
- selected housing content

before dropping into deeper operational areas like orders or chat.

## 7. Component Architecture

Phase 1 should establish a reusable component layer rather than styling each page from scratch.

Core shared components to define:

- top navigation
- main page shell / container
- hero section
- search dock
- quick-entry card grid
- editorial content strip
- featured listing card
- house listing row/card
- detail page media/header/actions section
- button variants
- input/search field variants
- empty state
- loading state / skeleton
- error state
- footer

Each component should answer one question clearly:

- what purpose it serves
- where it is allowed to be used
- what visual tokens it must follow

## 8. Page Scope for Phase 1

### 8.1 Must redesign

- homepage
- house list / search result page
- house detail page
- top navigation and global layout
- global design system and tokens

### 8.2 Unify lightly, not deeply

- login page
- register page
- message list
- "Mine" landing/entry area
- shared empty/loading/error states

These should match the new system, but they do not need full business redesign in phase 1.

### 8.3 Intentionally deferred

- deep chat experience
- advanced map search interactions
- complete order lifecycle polish
- placeholder subpages under "Mine"
- features that require new backend interfaces to be worthwhile

This keeps phase 1 focused on a visible upgrade rather than spreading effort across unfinished product areas.

## 9. Technical Direction

### 9.1 Framework choice

Keep `Vue 3 + Vite`.

Reasoning:

- the user already has some familiarity with Vue
- switching to React would increase risk without directly solving the design-system problem
- the main issue is frontend design cohesion, not framework capability

### 9.2 Styling direction

Do not continue with only ad hoc global CSS and page-local styles.

Recommended direction:

- keep Vue 3
- add a lightweight modern styling layer such as `UnoCSS` or `Tailwind CSS`
- define shared design tokens for color, radius, spacing, shadow, width, and typography

The implementation plan may choose one of these tools, but the design constraint is fixed: the team must introduce a system that makes consistency easier than inconsistency.

### 9.3 Layout behavior

Rules:

- desktop-first containers and section widths must be intentional
- mobile should degrade cleanly rather than being the source layout stretched to desktop
- all major pages must share the same shell logic and width rhythm

## 10. States and Behavior

Every key business page in phase 1 must handle:

- loading
- empty
- error
- success / populated state

Behavior rules:

- primary CTA must be visually obvious
- secondary actions must not compete with the primary one
- no page should show raw unfinished placeholders unless explicitly marked as unavailable
- data-light situations must still look designed, not broken

## 11. Data Flow and Backend Coupling

The redesign should avoid blocking on unfinished backend work.

Phase 1 data-flow rule:

- redesign around already available data first
- where backend gaps exist, use stable placeholders or graceful fallbacks inside the new visual system
- do not invent complex frontend-only flows that will likely be thrown away once real APIs arrive

For the first implementation wave, the frontend should mainly improve:

- presentation
- information hierarchy
- layout
- component consistency

rather than requiring new backend endpoints.

## 12. Error Handling

Error handling should be part of the visual system rather than an afterthought.

Rules:

- error messages should appear inside designed surfaces
- retry actions should be explicit where recovery is possible
- empty state copy should feel productized, not like debugging output
- network or data failures should not collapse the layout

## 13. Testing and Verification Expectations

Phase 1 verification should cover both implementation correctness and presentation consistency.

Required checks:

- desktop homepage has a clear first impression and coherent hierarchy
- homepage, list, and detail pages visibly belong to the same product
- desktop layout does not feel like a stretched mobile page
- mobile remains usable and visually intact
- no major overflow, spacing collapse, or broken visual hierarchy
- empty/loading/error states exist on redesigned pages

Implementation-stage verification should include:

- local build success
- targeted manual review of desktop and mobile breakpoints
- spot checks for the main navigation path: homepage -> list -> detail

## 14. Success Criteria

Phase 1 is successful when:

- the project looks intentional and memorable at first glance
- interviewers can read it as a productized project rather than a student demo
- the homepage creates identity without slowing access to product entry points
- list and detail pages continue the same visual language
- the redesign improves perception without depending on unfinished backend work

## 15. Open Decisions for Implementation Planning

The following items are intentionally left for the implementation plan, not the design:

- whether to use `UnoCSS` or `Tailwind CSS`
- the exact token naming structure
- whether to keep the existing route structure unchanged
- whether to refactor current pages in place or introduce a cleaner page shell first
- whether homepage and business pages should share one top navigation implementation or a small variant set

These are implementation concerns within the approved design direction, not unresolved product questions.

## 16. Recommended Next Step

The next step is to create a written implementation plan for phase 1 that:

- establishes the design foundation first
- then rebuilds homepage, list, and detail pages
- then harmonizes secondary pages into the same system

This preserves momentum while keeping scope controlled.

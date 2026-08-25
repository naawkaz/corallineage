# corallineage-frontend notes for agents

## Don't reset dialog form state with a "reset on open" `useEffect`

Several dialogs in this codebase used to share this exact shape:

```tsx
const [thing, setThing] = useState(...)
// ...other form state...

useEffect(() => {
  if (!dialogOpen) return
  setThing(defaultValue)
  // ...reset every other field...
}, [dialogOpen])
```

This was in `CreateVersionModal.tsx`, the old `AnnotationJobs.tsx` (the "Annotate with model"
dialog), `AnnotationReview.tsx` (the "Add to Source" dialog), and `ModelRegistry.tsx` (the "Train
New Model" dialog) - independently copy-pasted into all four. All four were fixed on 2026-08-21 by
extracting each dialog's form state into its own child component that only mounts while the
dialog is open (`{dialogOpen && <TheDialog ... />}`), or, for `CreateVersionModal` specifically
(which its parent keeps permanently mounted and toggles via an `open` prop instead of
conditionally rendering it), by having the exported component conditionally render an inner
component only while `open` is true.

`AnnotationJobs.tsx` itself was later deleted (2026-08-22): the "Validate model performance" and
"Upload & Annotate New Images" actions it hosted moved onto `training/ModelDetail.tsx` as two
separate buttons/dialogs (`ValidateModelDialog`/`UploadAndAnnotateDialog`, defined at the bottom
of that file), each already scoped to that page's one model - no model dropdown needed anymore.
The redundant dataset-wide job list it showed was replaced with a job list scoped to the current
model on `ModelDetail.tsx` itself. Both new dialogs follow the same mount-while-open pattern this
note describes; they're the reference example now instead of the deleted file.

**Why this matters, not just "it's extra renders":** a fresh mount already gives you default
state for free - a reset effect is redundant work at best. At worst, when one of the fields being
reset is *also* a dependency of another effect (e.g. `setActiveJob(null)` in a dialog that also
polls `activeJob` via `useEffect(..., [activeJob])`, or `setSelectedVersionId(...)` when another
effect fetches images keyed on `[selectedVersionId]`), the reset effect's own `setState` calls
retrigger those other effects, producing a second render pass on every single open before the
user sees anything. This actually happened in `ModelRegistry.tsx` and `AnnotationJobs.tsx`.

**If you're adding a new dialog with its own form state**, mount it conditionally
(`{open && <MyDialog onClose={...} .../>}`) and put the form's `useState` calls inside the dialog
component itself, not the parent page. Do not add a `useEffect` keyed on an `open`/`isOpen`
boolean whose only job is to call a bunch of `setX(default)`. If the parent must stay mounted
regardless of `open` (rare - only `CreateVersionModal` needed this, because `VersionsList.tsx`
holds it open across an in-progress import), use the wrapper-plus-inner-component split that file
now uses instead.

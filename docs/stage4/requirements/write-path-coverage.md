# Stage 4 write-path coverage contract

Every checked path must enqueue one final reconciliation event per affected source in the same transaction.

## Task paths

- create and AI breakdown confirmation
- title/description update
- status transition and reopen
- assign, reassign, and unassign
- delete and project cascade delete
- project/task recovery
- batch title rename and rollback
- accepted AI replan writes
- team-member removal/leave cleanup

## Weekly-review paths

- save, update, and delete
- PRIVATE/TEAM transition
- team, focus project, or shared summary change
- task-association replacement when it changes the focus/access projection
- AI polish draft confirmation
- membership termination affecting author project access

## Contract rule

Controllers never publish events. Direct Mapper writes outside the approved service/handler paths fail an architecture test or must be added to this matrix with a corresponding transactional test.

# Feature: Soft-delete + daily purge (messages / groups / notifications)

Uniform soft-delete pattern: every soft-deletable entity carries `deletedAt`; a user
action sets it (hidden immediately); a daily `DailyCleanupService` hard-purges rows whose
`deletedAt` is older than the retention window. Purging a group cascades to its related
notifications, messages, and participants.

Retention: messages 30d, groups 30d, notifications 30d. DIRECT (1:1) conversations are
never soft-deleted.

## Subphases

- [x] 1. Flyway V2 migration — add `deleted_at` (+ index) to `conversations` & `notifications`
- [x] 2. `Conversation` entity — add `deletedAt`, `isDeleted()`, `softDelete()`
- [x] 3. `Notification` entity — add `deletedAt`, `softDelete()`
- [x] 4. `ConversationService.deleteGroup` → soft-delete the group (keep owner check, lock, GROUP_DELETED broadcast)
- [x] 5. `NotificationService.delete` → soft-delete (new `NotificationRepository.softDelete`)
- [x] 6. Hide filters — `findAllForUser`, `findFeed`, unread-count, coalescing lookup all exclude `deletedAt`
- [x] 7. Purge repo methods — `MessageRepository.purgeDeletedBefore`, `NotificationRepository.purgeDeletedBefore` + `deleteByConversation`, `ConversationRepository.findGroupIdsToPurge`
- [x] 8. `DailyCleanupService` — `@Scheduled` daily; `purgeMessages()`, `purgeNotifications()`, `purgeGroups()` (per-group cascade via transactional `GroupPurger`)
- [x] 9. Config — `app.cleanup.interval-ms` + `app.cleanup.retention.{messages,groups,notifications}-days` in `application.yaml`
- [x] 10. Tests + verify — `DailyCleanupServiceTest`, adjust group/notification delete tests, `./mvnw test`, app boots with V2

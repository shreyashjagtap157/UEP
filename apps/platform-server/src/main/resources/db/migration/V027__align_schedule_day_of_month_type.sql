-- Match the JPA Integer mapping for ScheduleSeries.recurrenceDayOfMonth.
-- Keep this as a forward-only migration so deployed databases remain upgradeable.
ALTER TABLE schedule_series
    ALTER COLUMN recurrence_day_of_month
    TYPE integer
    USING recurrence_day_of_month::integer;

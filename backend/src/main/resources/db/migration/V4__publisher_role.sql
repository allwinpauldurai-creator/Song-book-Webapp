-- IMS Books - PUBLISHER role: may create books and manage the books they uploaded.

ALTER TABLE roles DROP CONSTRAINT ck_roles_name;
ALTER TABLE roles
    ADD CONSTRAINT ck_roles_name CHECK (name IN ('MEMBER', 'PUBLISHER', 'ADMIN'));

INSERT INTO roles (name) VALUES ('PUBLISHER');

-- Publishers list their own books by uploader.
CREATE INDEX IF NOT EXISTS ix_books_uploaded_by ON books (uploaded_by);

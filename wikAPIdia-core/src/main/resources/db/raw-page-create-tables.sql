CREATE TABLE IF NOT EXISTS RAW_PAGE (
  LANG_ID SMALLINT NOT NULL,
  PAGE_ID INT NOT NULL,
  REVISION_ID INT NOT NULL,
  BODY TEXT NOT NULL,
  TITLE VARCHAR(256) NOT NULL,
  LASTEDIT TIMESTAMP,
  NAME_SPACE SMALLINT NOT NULL,
  IS_REDIRECT BOOLEAN NOT NULL,
  IS_DISAMBIG BOOLEAN NOT NULL,
  REDIRECT_TITLE VARCHAR (256)
)


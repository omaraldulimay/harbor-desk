# One RDS instance holds the pool database and the silo databases of enterprise tenants.
# The master user is the "owner" role (migrations, onboarding). The app role harbor_app is created by the first migration.
resource "aws_db_subnet_group" "main" {
  name       = var.name
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_db_instance" "main" {
  identifier                  = var.name
  engine                      = "postgres"
  engine_version              = "16"
  instance_class              = var.db_instance_class
  allocated_storage           = 50
  storage_encrypted           = true
  db_name                     = "harbor"
  username                    = "harbor"
  manage_master_user_password = true # stored in Secrets Manager, never in state or code
  multi_az                    = true
  db_subnet_group_name        = aws_db_subnet_group.main.name
  vpc_security_group_ids      = [aws_security_group.db.id]
  backup_retention_period     = 7
  deletion_protection         = true
  skip_final_snapshot         = false
  final_snapshot_identifier   = "${var.name}-final"
}

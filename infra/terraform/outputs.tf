output "ecr_repository_url" {
  value = aws_ecr_repository.app.repository_url
}
output "ecs_cluster_name" {
  value = aws_ecs_cluster.app.name
}
output "rds_hostname" {
  value = aws_db_instance.payments.address
}
output "rds_master_secret_arn" {
  value = aws_db_instance.payments.master_user_secret[0].secret_arn
}

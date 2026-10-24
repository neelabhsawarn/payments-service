data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_caller_identity" "current" {}

resource "aws_vpc" "demo" {
  cidr_block           = "10.42.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true
  tags                 = { Name = "${var.project}-vpc" }
}

resource "aws_internet_gateway" "demo" {
  vpc_id = aws_vpc.demo.id
}

resource "aws_subnet" "public" {
  count                   = 2
  vpc_id                  = aws_vpc.demo.id
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  cidr_block              = cidrsubnet(aws_vpc.demo.cidr_block, 8, count.index)
  map_public_ip_on_launch = true
  tags                    = { Name = "${var.project}-public-${count.index + 1}" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.demo.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.demo.id
  }
}

resource "aws_route_table_association" "public" {
  count          = length(aws_subnet.public)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_security_group" "ecs" {
  name_prefix = "${var.project}-ecs-"
  description = "Outbound-only by default; optional restricted demo HTTP access"
  vpc_id      = aws_vpc.demo.id
  dynamic "ingress" {
    for_each = var.allowed_cidr == "" ? [] : [var.allowed_cidr]
    content {
      description = "Demo HTTP from a trusted IP only"
      from_port   = 8080
      to_port     = 8080
      protocol    = "tcp"
      cidr_blocks = [ingress.value]
    }
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "database" {
  name_prefix = "${var.project}-rds-"
  description = "Only ECS tasks may reach PostgreSQL"
  vpc_id      = aws_vpc.demo.id
  ingress {
    description     = "PostgreSQL from ECS tasks"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }
}

resource "aws_db_subnet_group" "demo" {
  name       = "${var.project}-db-subnets"
  subnet_ids = aws_subnet.public[*].id
}

resource "aws_db_instance" "payments" {
  identifier                 = "${var.project}-postgres"
  engine                     = "postgres"
  instance_class             = "db.t4g.micro"
  allocated_storage          = 20
  max_allocated_storage      = 30
  storage_type               = "gp3"
  storage_encrypted          = true
  db_name                    = "payments"
  username                   = "paymentsadmin"
  manage_master_user_password = true
  db_subnet_group_name       = aws_db_subnet_group.demo.name
  vpc_security_group_ids     = [aws_security_group.database.id]
  publicly_accessible        = false
  backup_retention_period    = 0
  deletion_protection        = false
  skip_final_snapshot        = true
  apply_immediately          = true
  # Portfolio only: RDS password lives in an AWS-managed Secrets Manager secret.
  # Do not use the admin account as the application user in a real environment.
}

resource "aws_ecr_repository" "app" {
  name         = "${var.project}-app"
  force_delete = true # Enables terraform destroy after demo images have been pushed.
  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${var.project}"
  retention_in_days = 7
}

resource "aws_ecs_cluster" "app" {
  name = "${var.project}-cluster"
}

data "aws_iam_policy_document" "ecs_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "execution" {
  name               = "${var.project}-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "rds_secret" {
  name = "read-rds-password"
  role = aws_iam_role.execution.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = ["secretsmanager:GetSecretValue"]
      Resource = [aws_db_instance.payments.master_user_secret[0].secret_arn]
    }]
  })
}

resource "aws_ecs_task_definition" "app" {
  count                    = var.deploy_service ? 1 : 0
  family                   = "${var.project}-task"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = aws_iam_role.execution.arn
  container_definitions = jsonencode([{
    name      = "payments-service"
    image     = "${aws_ecr_repository.app.repository_url}:${var.image_tag}"
    essential = true
    portMappings = [{ containerPort = 8080, protocol = "tcp" }]
    environment = [
      { name = "SPRING_DATASOURCE_URL", value = "jdbc:postgresql://${aws_db_instance.payments.address}:5432/payments" },
      { name = "SPRING_DATASOURCE_USERNAME", value = "paymentsadmin" },
      { name = "SPRING_KAFKA_BOOTSTRAP_SERVERS", value = var.kafka_bootstrap_servers },
      { name = "SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL", value = var.kafka_security_protocol }
    ]
    secrets = [{
      name      = "SPRING_DATASOURCE_PASSWORD"
      valueFrom = "${aws_db_instance.payments.master_user_secret[0].secret_arn}:password::"
    }]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.app.name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "payments"
      }
    }
  }])
}

resource "aws_ecs_service" "app" {
  count           = var.deploy_service ? 1 : 0
  name            = "${var.project}-service"
  cluster         = aws_ecs_cluster.app.id
  task_definition = aws_ecs_task_definition.app[0].arn
  desired_count   = 1
  launch_type     = "FARGATE"
  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = true
  }
  lifecycle {
    precondition {
      condition     = trimspace(var.kafka_bootstrap_servers) != ""
      error_message = "Specify a reachable Kafka endpoint before setting deploy_service=true."
    }
  }
  depends_on = [aws_iam_role_policy_attachment.execution, aws_iam_role_policy.rds_secret]
}

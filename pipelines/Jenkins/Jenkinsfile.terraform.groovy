 def secrets = [
    [path: 'secrets/credentials', engineVersion: 1, secretValues: [
        [envVar: 'PRIVATE_TOKEN', vaultKey: 'aws_access_key_id'],
        [envVar: 'PUBLIC_TOKEN', vaultKey: 'aws_secret_access_key'],
        [envVar: 'REGION', vaultKey: 'region'],
    ]],
]

def configuration = [
    vaultUrl: 'http://34.219.219.119:8200',
    vaultCredentialId: 'vault-approle',

    engineVersion: 1,
]

pipeline {
    agent any

    stages {
        stage('Vault') {
            steps {
                withVault([configuration: configuration, vaultSecrets: secrets]) {
                    echo "PRIVATE_TOKEN: " + env.PRIVATE_TOKEN
                    echo "PUBLIC_TOKEN: " + env.PUBLIC_TOKEN
                    echo "REGION: " + env.REGION
                }
            }
        }

        stage('Terraform Configuration Files') {
            steps {
                script {
                    // Check if Terraform configuration files exist
                    def configFileExists = fileExists('main.tf')
                    if (!configFileExists) {
                        // Generate Terraform configuration files if they don't exist
                        writeFile file: 'main.tf', text: '''
                            // main.tf
                            variable "aws_access_key_id" {}
                            variable "aws_secret_access_key" {}
                            variable "region" {}

                            // Configure AWS provider
                            provider "aws" {
                              access_key = var.aws_access_key_id
                              secret_key = var.aws_secret_access_key
                              region     = var.region
                            }

                            // Define AWS resources
                            resource "aws_instance" "example" {
                              ami           = "ami-08116b9957a259459"
                              instance_type = "t2.micro"

                              // Add a name tag to the instance
                              tags = {
                                Name = "iambananainstance" // 
                              }
			vpc_security_group_ids = ["sg-0eec660ba5b94d7b9"]
                            }
                        '''
                    }
                }
            }
        }

        stage('Terraform') {
            steps {
                script {
                    // Retrieve secrets from Vault
                    def vaultResponse = withVault([configuration: configuration, vaultSecrets: secrets]) {
                        return [PRIVATE_TOKEN: env.PRIVATE_TOKEN, PUBLIC_TOKEN: env.PUBLIC_TOKEN, REGION: env.REGION]
                    }
                    
                    // Write Terraform configuration with retrieved secrets
                    def terraformVars = """
                        aws_access_key_id = "${vaultResponse.PRIVATE_TOKEN}"
                        aws_secret_access_key = "${vaultResponse.PUBLIC_TOKEN}"
                        region = "${vaultResponse.REGION}" 
                    """
                    writeFile file: 'terraform.tfvars', text: terraformVars

                    // Initialize Terraform
                    sh 'terraform init'

                    // Apply Terraform configuration
                    sh 'terraform apply -auto-approve'
                }
            }
        }
    }

    post {
        always {
            // Clean up Terraform files after execution
            deleteDir()
        }
    }
}

def fileExists(String filename) {
    return fileExists(file: filename)

}
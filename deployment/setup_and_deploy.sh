#!/bin/bash

# Install Ansible and sshpass
sudo apt -y install ansible sshpass

# Add the Ansible PPA repository
sudo add-apt-repository -y ppa:ansible/ansible

# Update and upgrade Ansible
sudo apt update
sudo apt upgrade ansible -y

# Install the Docker community collection for Ansible
ansible-galaxy collection install community.docker

# Navigate to the deployment directory
cd ~/Practical-Distributed-Systems/deployment

# Run the Ansible playbook
ansible-playbook --extra-vars "ansible_user=<user> ansible_password=<password> ansible_ssh_extra_args='-o StrictHostKeyChecking=no'" -i hosts deployment.yaml

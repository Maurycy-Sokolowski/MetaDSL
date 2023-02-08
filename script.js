var staticScript = `site "Some App" with {
    add "Login" login show
        background "https://picsum.photos/1980/1020"
            script "https://api.scaleitusa.com/api/script/" key "scriptKey" with {
            add "Login" text
            add "Password" password
            add "Login" button with {
                transition "Scaleit iW8 main 0.8.47" with {
                    query post "https://api.scaleitusa.com/api/login" inputs {
                        "username" using "Login"
                        "password" using "Password"
                    }
                } 
            }
            add "Scaleit iW8" footer
        }
    add "Dashboard" dashboard
        header "Tickets" {
            query "https://api.scaleitusa.com/api/scaleit/v2/" use {
                rest "locationID"
                rest "tickets/summary"
                param "session="
                param "sessionKey"
                param "gqlQuery={tickets: dataQuery {recordCreated}}"
                param "gqlDataOnly=true"
                param "departmentId="
                param "departmentID"
            }
        }
        widgets {
            add "Tickets" doughnut "Number"
            days {
                "Monthly" 30
                "Weekly" 7
                "Daily" 1
            }
            use {
                query "https://api.scaleitusa.com/api/scaleit/v2/" use {
                    rest "locationID"
                    rest "tickets/summary"
                    param "session="
                    param "sessionKey"
                    param "gqlQuery={tickets: dataQuery {recordCreated}}"
                    param "gqlDataOnly=true"
                    param "departmentId="
                    param "departmentID"
                }
            }
            add "Tickets" doughnut "Amount"
            days {
                "Monthly" 30
                "Weekly" 7
                "Daily" 1
            }
            use {
                query "https://api.scaleitusa.com/api/scaleit/v2/" use {
                    rest "locationID"
                    rest "tickets/summary"
                    param "session="
                    param "sessionKey"
                    param "gqlQuery={tickets: dataQuery {recordCreated netPrice}}"
                    param "gqlDataOnly=true"
                    param "departmentId="
                    param "departmentID"
                } with {
                    column "netPrice" as "Price"
                }
            }
            add "Tickets" doughnut "Tonnage"
            days {
                "Monthly" 30
                "Weekly" 7
                "Daily" 1
            }
            use {
                query "https://api.scaleitusa.com/api/scaleit/v2/" use {
                    rest "locationID"
                    rest "tickets/summary"
                    param "session="
                    param "sessionKey"
                    param "gqlQuery={tickets: dataQuery {recordCreated  netWeight}}"
                    param "gqlDataOnly=true"
                    param "departmentId="
                    param "departmentID"
                } with {
                    column "netWeight" / 2000 as "Weight"
                }
            }
        }
            
    add "Products"
        title "Products"
        message "This summarizes the products"
        footer "Products"
        table display {
        query "https://api.scaleitusa.com/api/scaleit/v1/products/" use {
            rest "locationID"
            param "session="
            param "sessionKey"
        } with {
            column "id" as "Product ID"
            column  "name" as "Product Name"
            column "unitPrice" as "Unit Price" format "$ %5.2f"
            column "unitName" as "Unit"
            column "productGroupId" as "Product Type" format "name" is "id" dropdown "https://api.scaleitusa.com/api/scaleit/v1/productgroups/" use {
                rest "locationID"
                param "session="
                param "sessionKey"
            }
            add
                "name" is "Name" required
                "unitPrice" is "Unit Price" format "$ %5.2f"
                "unitId" is "Unit" format "name" is "id" dropdown "https://api.scaleitusa.com/api/scaleit/v1/units/" use {
                   rest "locationID"
                   param "session="
                    param "sessionKey"
                }
            as "Add"
            edit with delete
                "name" is "Name" required
                "unitPrice" is "Unit Price" format "$ %5.2f"
                "unitId" is "Unit" format "name" is "id" dropdown "https://api.scaleitusa.com/api/scaleit/v1/units/" use {
                   rest "locationID"
                   param "session="
                    param "sessionKey"
                }
            
            as "Edit"
        }
    }
    add "Trucks"
        title "Trucks"
        message "This summarizes the trucks"
        footer "Trucks"
        table display {
        query "https://api.scaleitusa.com/api/scaleit/v1/vehicles/" use {
            rest "locationID"
            param "session="
            param "sessionKey"
        } with {
            column "id" as "Truck ID"
            column  "name" as "Truck Name"
            column "ownerName" as "Owner"
            column "regNo" as "RegNo"
            column "vehicleWeights(0).tareWeight" as "Tare" format "%5.2f lb"
            edit with delete
                "regNo" is "RegNo"
                "name" is "Name"
                "ownerId" is "Owner" format "name" is "id" dropdown withblank "https://api.scaleitusa.com/api/scaleit/v1/clients/" use {
                   rest "locationID"
                   param "session="
                    param "sessionKey"
                }      
                "vehicleWeights" is "Vehicle Weights" multi {
                    "remarks" is "Name" required
                    "tareWeight" is "Weight" type Number format "%5.2f" required
            }                                               
            as "Edit"
            add
                "regNo" is "RegNo" required
                "name" is "Name"   
                "ownerId" is "Owner" format "name" is "id" dropdown withblank "https://api.scaleitusa.com/api/scaleit/v1/clients/" use {
                   rest "locationID"
                   param "session="
                    param "sessionKey"
                }                                                           
            as "Add"              
        }
    }
    add "Accounts"
        title "Accounts"
        message "This summarizes the accounts"
        footer "Accounts"
        table display {
        query "https://api.scaleitusa.com/api/scaleit/v1/clients/" use {
            rest "locationID"
            param "session="
            param "sessionKey"
        } with {
            column "id" as "Account ID"
            column  "name" as "Client Name"
            edit with delete
                "name" is "Name"
                "address1" is "Address 1"
                "address2" is "Address 2"
                "postOffice" is "City"
                "state" is "State"
                "postCodeText" is "Zip Code"
                "taxExempt" is "Tax Exempt" type Boolean
                "printPrice" is "Print Price" type Boolean
                "remarks" is "Remarks"
                "priceListId" is "Price List" format "name" is "id" dropdown withblank "https://api.scaleitusa.com/api/scaleit/v1/pricelist/" use {
                   rest "locationID"
                   param "session="
                    param "sessionKey"
                }                      
            as "Edit"
            add
                "name" is "Name"
                "address1" is "Address 1"
                "address2" is "Address 2"
                "postOffice" is "City"
                "state" is "State"
                "postCodeText" is "Zip Code"
                "taxExempt" is "Tax Exempt" type Boolean
                "printPrice" is "Print Price" type Boolean
                "remarks" is "Remarks"
                "priceListId" is "Price List" format "name" is "id" dropdown withblank "https://api.scaleitusa.com/api/scaleit/v1/pricelist/" use {
                   rest "locationID"
                   param "session="
                    param "sessionKey"
                }                      
            as "Add"
        }
    }
    add "W8 Users"
        title "W8 Users"
        message "Lists users available for W8"
        footer "W8 Users"
        table 10 display {
            query "https://api.scaleitusa.com/api/scaleit/v1/users/" use {
                rest "locationID"
                param "session="
                param "sessionKey"
            } with {
                column "id" as "User Id"
                column "recordCreated" as "Created Date"
                column "name" as "User Name"
                
                add
                    "name" is "Name" required
                    "password" is "Password" required
                    "email" is "Email"
                    "profileId" is "Profile" format "name" is "id" dropdown withblank "https://api.scaleitusa.com/api/scaleit/v1/userprofiles/" use { 
                        rest "locationID"
                        param "session="
                        param "sessionKey"
                    }
                as "Add"
            }
        }
    add "Projects"
        title "Projects"
        message "This summarizes the projects"
        footer "Projects"
        table display {
            query "https://api.scaleitusa.com/api/scaleit/v1/projects/" use {
                rest "locationID"
                param "session="
                param "sessionKey"
            } with {
                column "id" as "Project ID"
                column  "name" as "Project Name"
                column "client_name" as "Client"
                column "product_name" as "Product"
                column "taxExempt" as "Tax Exempt" format "Boolean"
                add
                    "name" is "Name" required                    
                    "address1" is "Address" 
                    "postOffice" is "City" 
                    "state" is "State" 
                    "address2" is "PO Number" 
                    "code" is "Job Number" 
                    "orderedQuantity" is "Ordered" type Number format "%5.2f" required
                    "taxExempt" is "Tax Exempt" type Boolean
                    "productId" is "Product" format "name" is "id" dropdown withblank "https://api.scaleitusa.com/api/scaleit/v1/products/" use {
                        rest "locationID"
                        param "session="
                        param "sessionKey"
                    }                    
                    "clientId" is "Customer" format "name" is "id" dropdown withblank "https://api.scaleitusa.com/api/scaleit/v1/clients/" use {
                        rest "locationID"
                        param "session="
                        param "sessionKey"
                    }					
                    "priceListId" is "Price List" format "name" is "id" dropdown withblank "https://api.scaleitusa.com/api/scaleit/v1/pricelist/" use {
                        rest "locationID"
                        param "session="
                        param "sessionKey"
                    }
                    "projectTypeId" is "Type" format "name" is "id" dropdown withblank "https://api.scaleitusa.com/api/scaleit/v1/projecttypes/" use {
                        rest "locationID"
                        param "session="
                        param "sessionKey"
                    }				
                as "Add a new Project"
                edit with delete
                    "name" is "Name" required                    
                    "address1" is "Address" 
                    "postOffice" is "City" 
                    "state" is "State" 
                    "address2" is "PO Number" 
                    "code" is "Job Number" 
                    "orderedQuantity" is "Ordered" type Number format "%5.2f" required
                    "taxExempt" is "Tax Exempt" type Boolean
                    "productId" is "Product" format "name" is "id" dropdown withblank "https://api.scaleitusa.com/api/scaleit/v1/products/" use {
                        rest "locationID"
                        param "session="
                        param "sessionKey"
                    }                        
                    "clientId" is "Customer" format "name" is "id" dropdown withblank "https://api.scaleitusa.com/api/scaleit/v1/clients/" use {
                        rest "locationID"
                        param "session="
                        param "sessionKey"
                    }
                    "priceListId" is "Price List" format "name" is "id" dropdown withblank "https://api.scaleitusa.com/api/scaleit/v1/pricelist/" use {
                        rest "locationID"
                        param "session="
                        param "sessionKey"
                    }
                    "projectTypeId" is "Type" format "name" is "id" dropdown withblank "https://api.scaleitusa.com/api/scaleit/v1/projecttypes/" use {
                        rest "locationID"
                        param "session="
                        param "sessionKey"
                    }			
                as "Edit"
            }
        }
    add "Tickets"
        title "Tickets for the last week"
        message "This summarizes the tickets for this week"
        footer "Weekly tickets"
        table 1 display {
            query "https://api.scaleitusa.com/api/scaleit/v1/tickets/" use {
                rest "locationID"
                param "session="
                param "sessionKey"
                param "departmentId="
                param "departmentID"
            } with {
                column "depRunningNo" as "Number"
                column "finished" as "Finished" format "MM-DD-YYYY h:mm a"
                column "clientName" as "Client Name"
                column "licensePlate" as "Vehicle RegNo"
                column "notes" as "Driver"
                column "projectName" as "Project"
                column "ticketDetails(0).netWeight" / 2000 as "Net Weight (tons)"
                media "fileName" as "Ticket"
            }
        }
    add "Price Lists"
        title "Price List"
        message "Price List"
        footer "Price List"
        table display {
            query "https://api.scaleitusa.com/api/scaleit/v1/pricelist/" use {
                rest "locationID"
                param "session="
                param "sessionKey"
                param "departmentId="
                param "departmentID"
            } with {
                column "id" as "ID"
                column  "name" as "Name"
                edit
                    "name" is "Name"
                    "priceListDetails" is "Price List Details" multi {
                        "productId" is "Product" format "name" is "id" dropdown "https://api.scaleitusa.com/api/scaleit/v1/products/" use {
                            rest "locationID"
                            param "session="
                            param "sessionKey"
                        }
                        "value" is "Value" type Number format "$ %5.2f"
                }                    
                as "Edit"
                add
                    "name" is "Name" required
                as "Add"               
            }
        }
    add "Reports" menu entries {
        add "Grouped Tickets"
            title "Grouped Tickets for the last week"
            message "This summarizes the grouped tickets for this week"
            footer "Weekly grouped tickets"
            report 1 {
            query "https://api.scaleitusa.com/api/scaleit/v1/reports/" use {
                rest "locationID"
                param "session="
                param "sessionKey"
                param "departmentId="
                param "departmentID"
            } with {
                column "totalTicketCount" as "Total tickets"
                column "totalWeight" / 2000 as "Total Weight" format "%8.2f"
                column "tickets.ticketNumber" as "Ticket Nbr."
                column "tickets.finished" as "Finished" format "MM-DD-YYYY h:mm a"
                column "tickets.clientName" as "Client Name"
                column "tickets.licensePlate" as "Vehicle RegNo"
                column "tickets.driverName" as "Driver Name"
                column "tickets.projectName" as "Project"
                column "tickets.productName" as "Product"
                column "tickets.ticketWeight" / 2000  as "Weight" format "%d"
                media "fileName" as "Ticket"
            }
        }
        add "Grouped Tickets by Customer"
            title "Grouped Tickets by Customer"
            message "This summarizes the grouped tickets by customer"
            footer "Weekly grouped tickets by customer"
            report 1 {
            query "https://api.scaleitusa.com/api/scaleit/v1/reports/" use {
                rest "locationID"
                param "grouping=clients"
                param "session="
                param "sessionKey"
                param "departmentId="
                param "departmentID"
            } with {
                column "totalTicketCount" as "Total tickets"
                column "totalWeight" / 2000 as "Total Weight" format "%8.2f"
                column "clients.clientName" as "Client Name"
                column "clients.clientTicketCount" as "Total tickets"
                column "clients.clientTotalWeight" / 2000 as "Total Weight" format "%d"
                column "clients.tickets.ticketNumber" as "Ticket Nbr."
                column "clients.tickets.finished" as "Finished" format "MM-DD-YYYY h:mm a"
                column "clients.tickets.licensePlate" as "Vehicle RegNo"
                column "clients.tickets.driverName" as "Driver Name"
                column "clients.tickets.projectName" as "Project"
                column "clients.tickets.ticketWeight" / 2000  as "Weight" format "%d"
                media "fileName" as "Ticket"
            }
        }
        add "Grouped Tickets by Product"
            title "Grouped Tickets by Product"
            message "This summarizes the grouped tickets by product"
            footer "Grouped tickets by product"
            report 1 {
            query "https://api.scaleitusa.com/api/scaleit/v1/reports/" use {
                rest "locationID"
                param "grouping=products"
                param "session="
                param "sessionKey"
                param "departmentId="
                param "departmentID"
            } with {
                column "totalTicketCount" as "Total tickets"
                column "totalWeight"  as "Total Weight" format "%5.0f"
                column "totalPrice" as "Total Price" format "$ %5.2f"
                column "totalPriceWithTax" as "Total Price with Tax" format "$ %5.2f"
                column "products.productName" as "Product Name"
                column "products.productTicketCount" as "Total tickets"
                column "products.productTotalWeight"  as "Total Weight" format "%5.0f"
                column "products.productTotalPrice" as "Total Price" format "$ %5.2f"
                column "products.productTotalPriceWithTax" as "Total Price with Tax" format "$ %5.2f"
                column "products.totalPriceWithTax" as "Total Price with Tax" format "$ %5.2f"
                column "products.tickets.ticketNumber" as "Ticket Nbr."
                column "products.tickets.finished" as "Finished" format "MMMM Do YYYY, h:mm a"
                column "products.tickets.clientName" as "Client"
                column "products.tickets.projectName" as "Project"
                column "products.tickets.ticketWeight"  as "Weight" format "%5.0f"
                column "products.tickets.ticketPrice" as "Price" format "$ %5.2f"
                column "products.tickets.ticketPriceWithTax" as "Price with Tax" format "$ %5.2f"
            }
        }
    }
    add "Scaleit iW8 main 0.8.47" main color "orange" with {
    add "username" display
        add "dateswindow" datetimedialog buttons {
            add "Day" 1
            add "Week" 7
            add "Month" 30
            add "Year" 365
        }
        add "Company,companies,companyID,companyName,locations,locationID,locationName,departments,departmentID,name" menu with {
            transition	 "Dashboard"
        }
        add "Scaleit iW8" footer
        add "Dashboard" load
    }
}`;